package com.relaycrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

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

    protected ResultActions doPut(String apiKey, String path, String json) throws Exception {
        return mvc.perform(put(path).header("X-Api-Key", apiKey).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions doDelete(String apiKey, String path) throws Exception {
        return mvc.perform(delete(path).header("X-Api-Key", apiKey));
    }

    /** Creates a record and returns its id. */
    protected long create(String apiKey, String path, String json) throws Exception {
        String body = doPost(apiKey, path, json).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected static String contactJson(String lastName) {
        return "{\"firstName\":\"Test\",\"lastName\":\"" + lastName + "\"}";
    }
}
