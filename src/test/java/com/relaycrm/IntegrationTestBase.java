package com.relaycrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import com.jayway.jsonpath.JsonPath;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the whole application against a real PostgreSQL (Testcontainers), with
 * the demo data loaded. As in production, migrations run as the owner role and
 * the application connects as the restricted relaycrm_app role, so row-level
 * security is active in tests too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
public abstract class IntegrationTestBase {

    // Demo users (see db/demo/R__demo_data.sql)
    protected static final String ACME_ADMIN = "acme-admin-key";         // user 1
    protected static final String ACME_MANAGER = "acme-manager-key";     // user 2
    protected static final String ACME_REP_RITA = "acme-rep-rita-key";   // user 3
    protected static final String ACME_REP_RAVI = "acme-rep-ravi-key";   // user 4
    protected static final String GLOBEX_ADMIN = "globex-admin-key";     // user 5
    protected static final String GLOBEX_REP = "globex-rep-key";         // user 6

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    protected ResultActions doGet(String apiKey, String path) throws Exception {
        return mvc.perform(get(path).header("X-Api-Key", apiKey));
    }

    protected ResultActions doPost(String apiKey, String path, String json) throws Exception {
        return mvc.perform(post(path).header("X-Api-Key", apiKey).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** PUT based on the record's current version, as a well-behaved client would send it. */
    protected ResultActions doPut(String apiKey, String path, String json) throws Exception {
        return doPut(apiKey, path, currentEtag(apiKey, path), json);
    }

    /** PUT with an explicit If-Match value; null sends no If-Match header. */
    protected ResultActions doPut(String apiKey, String path, String ifMatch, String json) throws Exception {
        return mvc.perform(withIfMatch(put(path), ifMatch).header("X-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** PATCH with a JSON Merge Patch body, based on the record's current version. */
    protected ResultActions doPatch(String apiKey, String path, String json) throws Exception {
        return doPatch(apiKey, path, currentEtag(apiKey, path), json);
    }

    /** PATCH with an explicit If-Match value; null sends no If-Match header. */
    protected ResultActions doPatch(String apiKey, String path, String ifMatch, String json) throws Exception {
        return mvc.perform(withIfMatch(patch(path), ifMatch).header("X-Api-Key", apiKey)
                .contentType("application/merge-patch+json").content(json));
    }

    /**
     * The ETag the caller gets when reading the record. A caller who cannot read
     * it (another organization, or no such id) sends a guess, "0": those requests
     * are refused before the version is looked at.
     */
    protected String currentEtag(String apiKey, String path) throws Exception {
        String etag = doGet(apiKey, path).andReturn().getResponse().getHeader("ETag");
        return etag != null ? etag : "\"0\"";
    }

    private static MockHttpServletRequestBuilder withIfMatch(MockHttpServletRequestBuilder request, String ifMatch) {
        return ifMatch == null ? request : request.header("If-Match", ifMatch);
    }

    protected ResultActions doDelete(String apiKey, String path) throws Exception {
        return mvc.perform(delete(path).header("X-Api-Key", apiKey));
    }

    /** Creates a record and returns its id. */
    protected long create(String apiKey, String path, String json) throws Exception {
        String body = doPost(apiKey, path, json).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /**
     * A direct connection as the role that owns the tables (in this test database
     * also a superuser): not limited by row-level security, so tests can look at
     * what is really stored.
     */
    protected static Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Runs a "select count(*) ..." (or any single-number query) as the owner role. */
    protected static long ownerQueryForLong(String query) throws Exception {
        try (Connection owner = ownerConnection();
             Statement sql = owner.createStatement();
             ResultSet rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    /**
     * An id that no row of this table has, in any organization, and will not get
     * during the test: well above the highest id handed out so far. Use it for
     * "this record does not exist" instead of a fixed number, which would only
     * be right as long as no other test happened to create that many rows.
     */
    protected static long unusedId(String table) throws Exception {
        return ownerQueryForLong("select coalesce(max(id), 0) + 1000 from " + table);
    }

    protected static String contactJson(String lastName) {
        return "{\"firstName\":\"Test\",\"lastName\":\"" + lastName + "\"}";
    }
}
