package com.relaycrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Optimistic locking: an update must say which version of the record it is
 * based on (If-Match), and is refused if the record has changed since.
 */
class OptimisticLockingTest extends IntegrationTestBase {

    private static final String V0 = "\"0\"";
    private static final String V1 = "\"1\"";
    private static final String CONTACT = "{\"firstName\":\"Lock\",\"lastName\":\"Original\"}";

    /** path, body to create, body for a PUT that changes the name, the name field. */
    static Stream<Arguments> recordTypes() {
        return Stream.of(
                Arguments.of("/api/contacts", CONTACT,
                        "{\"firstName\":\"Lock\",\"lastName\":\"First\"}", "lastName"),
                Arguments.of("/api/leads", "{\"name\":\"Original\"}",
                        "{\"name\":\"First\",\"status\":\"NEW\"}", "name"),
                Arguments.of("/api/opportunities", "{\"name\":\"Original\"}",
                        "{\"name\":\"First\",\"stage\":\"PROSPECTING\"}", "name"));
    }

    // --- The version is visible ---

    @ParameterizedTest
    @MethodSource("recordTypes")
    void aNewRecordIsVersionZeroInTheBodyAndTheEtag(String path, String createBody) throws Exception {
        doPost(ACME_REP_RITA, path, createBody)
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", V0))
                .andExpect(jsonPath("$.version", is(0)));
        long id = create(ACME_REP_RITA, path, createBody);
        doGet(ACME_REP_RITA, path + "/" + id)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", V0))
                .andExpect(jsonPath("$.version", is(0)));
    }

    // --- Matching and stale versions ---

    @ParameterizedTest
    @MethodSource("recordTypes")
    void updateWithTheCurrentVersionSucceedsAndAStaleOneIsRefused(
            String path, String createBody, String putBody, String nameField) throws Exception {
        String record = path + "/" + create(ACME_REP_RITA, path, createBody);

        // First editor read version 0 and saves: accepted, the record is now version 1.
        doPut(ACME_REP_RITA, record, V0, putBody)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", V1))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$." + nameField, is("First")));

        // Second editor also read version 0: refused, by PUT and by PATCH.
        doPut(ACME_ADMIN, record, V0, putBody.replace("First", "Second"))
                .andExpect(status().isPreconditionFailed());
        doPatch(ACME_ADMIN, record, V0, "{\"" + nameField + "\":\"Second\"}")
                .andExpect(status().isPreconditionFailed());

        // The record is exactly as the first editor left it.
        doGet(ACME_REP_RITA, record)
                .andExpect(header().string("ETag", V1))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$." + nameField, is("First")));

        // After reading again, the second editor can save.
        doPatch(ACME_ADMIN, record, V1, "{\"" + nameField + "\":\"Second\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.version", is(2)))
                .andExpect(jsonPath("$." + nameField, is("Second")));
    }

    @Test
    void aVersionFromTheFutureIsAlsoRefused() throws Exception {
        String record = "/api/contacts/" + create(ACME_REP_RITA, "/api/contacts", CONTACT);
        doPatch(ACME_REP_RITA, record, "\"7\"", "{\"lastName\":\"Changed\"}")
                .andExpect(status().isPreconditionFailed());
    }

    // --- Missing or malformed If-Match ---

    @ParameterizedTest
    @MethodSource("recordTypes")
    void updateWithoutIfMatchIsRefusedWith428(
            String path, String createBody, String putBody, String nameField) throws Exception {
        String record = path + "/" + create(ACME_REP_RITA, path, createBody);

        doPut(ACME_REP_RITA, record, null, putBody)
                .andExpect(status().isPreconditionRequired());
        doPatch(ACME_REP_RITA, record, null, "{\"" + nameField + "\":\"First\"}")
                .andExpect(status().isPreconditionRequired());

        doGet(ACME_REP_RITA, record)
                .andExpect(jsonPath("$.version", is(0)))
                .andExpect(jsonPath("$." + nameField, is("Original")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "W/\"0\"", "\"0\", \"1\"", "\"abc\"", "0", "\"\"", "\"-1\""})
    void onlyASingleQuotedNumberIsAcceptedAsIfMatch(String ifMatch) throws Exception {
        String record = "/api/contacts/" + create(ACME_REP_RITA, "/api/contacts", CONTACT);

        doPatch(ACME_REP_RITA, record, ifMatch, "{\"lastName\":\"Changed\"}")
                .andExpect(status().isBadRequest());
        doPut(ACME_REP_RITA, record, ifMatch, "{\"firstName\":\"Lock\",\"lastName\":\"Changed\"}")
                .andExpect(status().isBadRequest());

        doGet(ACME_REP_RITA, record)
                .andExpect(jsonPath("$.version", is(0)))
                .andExpect(jsonPath("$.lastName", is("Original")));
    }

    @Test
    void twoIfMatchHeadersAreRefusedLikeAList() throws Exception {
        String record = "/api/contacts/" + create(ACME_REP_RITA, "/api/contacts", CONTACT);
        mvc.perform(patch(record).header("X-Api-Key", ACME_REP_RITA)
                        .header("If-Match", V0).header("If-Match", V1)
                        .contentType("application/merge-patch+json").content("{\"lastName\":\"Changed\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- A PATCH that changes nothing ---

    @Test
    void anUpdateThatChangesNothingKeepsTheVersion() throws Exception {
        String record = "/api/contacts/" + create(ACME_REP_RITA, "/api/contacts", CONTACT);

        doPatch(ACME_REP_RITA, record, V0, "{}")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", V0))
                .andExpect(jsonPath("$.version", is(0)));
        doPatch(ACME_REP_RITA, record, V0, "{\"lastName\":\"Original\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", V0))
                .andExpect(jsonPath("$.version", is(0)));
    }

    // --- Order of the checks ---

    @Test
    void accessIsCheckedBeforeTheVersion() throws Exception {
        String record = "/api/contacts/" + create(ACME_REP_RITA, "/api/contacts", CONTACT);

        // Another organization: 404 whatever the If-Match header says, or if it is missing.
        doPatch(GLOBEX_ADMIN, record, "\"99\"", "{\"lastName\":\"Hijacked\"}").andExpect(status().isNotFound());
        doPatch(GLOBEX_ADMIN, record, V0, "{\"lastName\":\"Hijacked\"}").andExpect(status().isNotFound());
        doPatch(GLOBEX_ADMIN, record, null, "{\"lastName\":\"Hijacked\"}").andExpect(status().isNotFound());
        doPut(GLOBEX_ADMIN, record, "\"99\"", CONTACT).andExpect(status().isNotFound());

        // Same organization, not the owner: 403, never 412 or 428.
        doPatch(ACME_REP_RAVI, record, "\"99\"", "{\"lastName\":\"Ravis\"}").andExpect(status().isForbidden());
        doPatch(ACME_REP_RAVI, record, null, "{\"lastName\":\"Ravis\"}").andExpect(status().isForbidden());

        doGet(ACME_REP_RITA, record)
                .andExpect(jsonPath("$.version", is(0)))
                .andExpect(jsonPath("$.lastName", is("Original")));
    }

    @Test
    void theVersionIsCheckedBeforeTheBody() throws Exception {
        String record = "/api/contacts/" + create(ACME_REP_RITA, "/api/contacts", CONTACT);
        // A stale version and an invalid body: the caller is told about the version.
        doPatch(ACME_REP_RITA, record, "\"99\"", "{\"lastName\":null,\"nope\":1}")
                .andExpect(status().isPreconditionFailed());
        doPut(ACME_REP_RITA, record, null, "{}")
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void versionCannotBeSentInTheBody() throws Exception {
        String record = "/api/contacts/" + create(ACME_REP_RITA, "/api/contacts", CONTACT);
        doPatch(ACME_REP_RITA, record, V0, "{\"version\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version", is("unknown field")));
    }

    // --- Real races, forced with a row lock ---

    /**
     * Both requests carry If-Match "0" and both load version 0, so both pass the
     * header check. Only the database can stop the second write, and it does.
     */
    @Test
    void whenTwoSavesRaceExactlyOneWinsAndTheOtherGets412() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        String record = "/api/contacts/" + id;

        List<Integer> statuses = raceOnLockedRow("contacts", id,
                () -> doPatch(ACME_REP_RITA, record, V0, "{\"lastName\":\"Rita\"}")
                        .andReturn().getResponse().getStatus(),
                () -> doPatch(ACME_ADMIN, record, V0, "{\"lastName\":\"Alice\"}")
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(200, 412);
        doGet(ACME_REP_RITA, record)
                .andExpect(header().string("ETag", V1))
                .andExpect(jsonPath("$.version", is(1)));
    }

    @Test
    void whenTwoDeletesRaceTheSecondGets404() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        String record = "/api/contacts/" + id;
        Callable<Integer> deleteIt = () -> mvc.perform(delete(record).header("X-Api-Key", ACME_ADMIN))
                .andReturn().getResponse().getStatus();

        List<Integer> statuses = raceOnLockedRow("contacts", id, deleteIt, deleteIt);

        assertThat(statuses).containsExactlyInAnyOrder(204, 404);
        doGet(ACME_ADMIN, record).andExpect(status().isNotFound());
    }

    @Test
    void deleteDoesNotCheckTheVersion() throws Exception {
        String record = "/api/contacts/" + create(ACME_REP_RITA, "/api/contacts", CONTACT);
        doPatch(ACME_REP_RITA, record, V0, "{\"lastName\":\"Edited\"}").andExpect(status().isOk());
        // Known gap: the admin deletes without saying which version they looked at.
        doDelete(ACME_ADMIN, record).andExpect(status().isNoContent());
        doDelete(ACME_ADMIN, record).andExpect(status().isNotFound());
    }

    /**
     * Runs two requests so that they are guaranteed to overlap. The test locks
     * the row first, so each request can read the record but then has to wait
     * when it tries to write. Once PostgreSQL reports both of them waiting, the
     * lock is released and they write one after the other.
     */
    private List<Integer> raceOnLockedRow(String table, long id, Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection lock = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement sql = lock.createStatement()) {
            lock.setAutoCommit(false);
            sql.execute("select id from " + table + " where id = " + id + " for update");

            Future<Integer> a = pool.submit(first);
            Future<Integer> b = pool.submit(second);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (waitingForALock(sql) < 2) {
                assertThat(System.nanoTime()).as("both requests should be waiting on the row lock").isLessThan(deadline);
                Thread.sleep(50);
            }
            lock.commit();

            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private static int waitingForALock(Statement sql) throws Exception {
        try (ResultSet rows = sql.executeQuery("select count(*) from pg_stat_activity "
                + "where datname = current_database() and wait_event_type = 'Lock'")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
