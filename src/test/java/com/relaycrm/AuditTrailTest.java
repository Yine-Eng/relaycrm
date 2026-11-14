package com.relaycrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.stream.Stream;

import javax.sql.DataSource;

import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.CurrentUserContext;
import com.relaycrm.auth.Role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Audit trail: every saved change writes exactly one event, in the same
 * transaction; nothing else writes one; and events can never be changed.
 */
class AuditTrailTest extends IntegrationTestBase {

    private static final String CONTACT = "{\"firstName\":\"Audit\",\"lastName\":\"Original\",\"phone\":\"555-0100\"}";

    @Autowired
    DataSource dataSource;

    @Autowired
    PlatformTransactionManager transactionManager;

    /** path, entity type, body to create, the name field. */
    static Stream<Arguments> recordTypes() {
        return Stream.of(
                Arguments.of("/api/contacts", "CONTACT", "{\"firstName\":\"Audit\",\"lastName\":\"Original\"}", "lastName"),
                Arguments.of("/api/leads", "LEAD", "{\"name\":\"Original\"}", "name"),
                Arguments.of("/api/opportunities", "OPPORTUNITY", "{\"name\":\"Original\"}", "name"));
    }

    // --- Create, update and delete each write one event, with the actor ---

    @ParameterizedTest
    @MethodSource("recordTypes")
    void createUpdateAndDeleteEachWriteOneEvent(String path, String entityType, String createBody, String nameField)
            throws Exception {
        long id = create(ACME_REP_RITA, path, createBody);            // Rita is user 3
        String record = path + "/" + id;

        doGet(ACME_REP_RITA, record + "/history")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action", is("CREATE")))
                .andExpect(jsonPath("$[0].actorUserId", is(3)))
                .andExpect(jsonPath("$[0].entityType", is(entityType)))
                .andExpect(jsonPath("$[0].entityId", is((int) id)))
                .andExpect(jsonPath("$[0].versionAfter", is(0)))
                .andExpect(jsonPath("$[0].createdAt", notNullValue()))
                .andExpect(jsonPath("$[0].changes." + nameField + ".to", is("Original")))
                .andExpect(jsonPath("$[0].changes.ownerId.to", is(3)))
                .andExpect(jsonPath("$[0].orgId").doesNotExist());

        doPatch(ACME_MANAGER, record, "{\"" + nameField + "\":\"Renamed\"}")   // Mark is user 2
                .andExpect(status().isOk());

        doGet(ACME_REP_RITA, record + "/history")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].action", is("UPDATE")))
                .andExpect(jsonPath("$[0].actorUserId", is(2)))
                .andExpect(jsonPath("$[0].versionAfter", is(1)))
                // Only the field that changed, with its old and new value.
                .andExpect(jsonPath("$[0].changes",
                        is(Map.of(nameField, Map.of("from", "Original", "to", "Renamed")))))
                .andExpect(jsonPath("$[1].action", is("CREATE")));

        doDelete(ACME_ADMIN, record).andExpect(status().isNoContent());           // Alice is user 1

        doGet(ACME_ADMIN, record + "/history")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].action", is("DELETE")))
                .andExpect(jsonPath("$[0].actorUserId", is(1)))
                .andExpect(jsonPath("$[0].versionAfter", is(1)))
                // The snapshot: the record's last values.
                .andExpect(jsonPath("$[0].changes." + nameField + ".from", is("Renamed")))
                .andExpect(jsonPath("$[0].changes.ownerId.from", is(3)))
                .andExpect(jsonPath("$[1].action", is("UPDATE")))
                .andExpect(jsonPath("$[2].action", is("CREATE")));

        assertThat(eventCount(entityType, id)).isEqualTo(3);
    }

    @Test
    void whoMovedTheDealToWonAndWhatWasItBefore() throws Exception {
        long id = create(ACME_REP_RITA, "/api/opportunities",
                "{\"name\":\"Big deal\",\"amount\":5000,\"stage\":\"PROPOSAL\"}");
        String record = "/api/opportunities/" + id;

        doPatch(ACME_MANAGER, record, "{\"stage\":\"WON\"}").andExpect(status().isOk());

        doGet(ACME_REP_RAVI, record + "/history")
                .andExpect(jsonPath("$[0].action", is("UPDATE")))
                .andExpect(jsonPath("$[0].actorUserId", is(2)))
                .andExpect(jsonPath("$[0].changes", is(Map.of("stage", Map.of("from", "PROPOSAL", "to", "WON")))));
    }

    @Test
    void putRecordsClearedFieldsAndReassignment() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        String record = "/api/contacts/" + id;

        // PUT without the phone clears it; the admin also hands the contact to Ravi (user 4).
        doPut(ACME_ADMIN, record, "{\"firstName\":\"Audit\",\"lastName\":\"Original\",\"ownerId\":4}")
                .andExpect(status().isOk());

        doGet(ACME_ADMIN, record + "/history")
                .andExpect(jsonPath("$[0].changes.phone.from", is("555-0100")))
                .andExpect(jsonPath("$[0].changes.phone.to").value((Object) null))
                .andExpect(jsonPath("$[0].changes.ownerId.from", is(3)))
                .andExpect(jsonPath("$[0].changes.ownerId.to", is(4)))
                .andExpect(jsonPath("$[0].changes.firstName").doesNotExist())
                .andExpect(jsonPath("$[0].changes.lastName").doesNotExist());
    }

    // --- Nothing else writes an event ---

    @Test
    void anUpdateThatChangesNothingWritesNoEvent() throws Exception {
        long contact = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        doPatch(ACME_REP_RITA, "/api/contacts/" + contact, "{}").andExpect(status().isOk());
        doPatch(ACME_REP_RITA, "/api/contacts/" + contact, "{\"lastName\":\"Original\"}").andExpect(status().isOk());
        doPut(ACME_REP_RITA, "/api/contacts/" + contact, CONTACT).andExpect(status().isOk());
        assertThat(eventCount("CONTACT", contact)).isEqualTo(1);

        // 3000 and 3000.00 are the same amount: no new version, and no event.
        long deal = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Deal\",\"amount\":3000.00}");
        doPatch(ACME_REP_RITA, "/api/opportunities/" + deal, "{\"amount\":3000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(0)));
        assertThat(eventCount("OPPORTUNITY", deal)).isEqualTo(1);
    }

    @Test
    void requestsThatFailWriteNoEvent() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        String record = "/api/contacts/" + id;
        long before = eventCount();

        doPost(ACME_REP_RITA, "/api/contacts", "{\"firstName\":\"No last name\"}")
                .andExpect(status().isBadRequest());
        doPost(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Sneaky\",\"contactId\":3}")
                .andExpect(status().isBadRequest());
        doPatch(ACME_REP_RITA, record, "{\"lastName\":null}").andExpect(status().isBadRequest());
        doPatch(ACME_REP_RITA, record, "{\"nope\":1}").andExpect(status().isBadRequest());
        doPatch(ACME_REP_RAVI, record, "{\"lastName\":\"Ravis\"}").andExpect(status().isForbidden());
        doPatch(ACME_REP_RITA, record, "{\"ownerId\":4}").andExpect(status().isForbidden());
        doPatch(GLOBEX_ADMIN, record, "{\"lastName\":\"Hijacked\"}").andExpect(status().isNotFound());
        doPatch(ACME_REP_RITA, record, "\"99\"", "{\"lastName\":\"Stale\"}").andExpect(status().isPreconditionFailed());
        doPatch(ACME_REP_RITA, record, null, "{\"lastName\":\"No version\"}").andExpect(status().isPreconditionRequired());
        doDelete(ACME_REP_RITA, record).andExpect(status().isForbidden());
        doDelete(GLOBEX_ADMIN, record).andExpect(status().isNotFound());
        doDelete(ACME_ADMIN, "/api/contacts/999999").andExpect(status().isNotFound());

        assertThat(eventCount()).isEqualTo(before);
        assertThat(eventCount("CONTACT", id)).isEqualTo(1);
    }

    @Test
    void deletingTwiceWritesOneDeleteEvent() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        doDelete(ACME_ADMIN, "/api/contacts/" + id).andExpect(status().isNoContent());
        doDelete(ACME_ADMIN, "/api/contacts/" + id).andExpect(status().isNotFound());
        assertThat(eventCount("CONTACT", id)).isEqualTo(2);   // CREATE and one DELETE
    }

    /**
     * The other half of "same transaction": if the event cannot be written, the
     * change is not saved either. The test makes the database refuse events for
     * one record (a temporary constraint) and then tries to change that record.
     */
    @Test
    void aChangeIsNotSavedIfItsEventCannotBeWritten() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        String record = "/api/contacts/" + id;

        try (Connection owner = ownerConnection(); Statement sql = owner.createStatement()) {
            sql.execute("alter table audit_events add constraint refuse_one_record "
                    + "check (entity_id <> " + id + ") not valid");
            try {
                assertThatThrownBy(() -> doPatch(ACME_REP_RITA, record, "\"0\"", "{\"lastName\":\"Unaudited\"}"))
                        .rootCause()
                        .hasMessageContaining("refuse_one_record");
                assertThatThrownBy(() -> doDelete(ACME_ADMIN, record))
                        .rootCause()
                        .hasMessageContaining("refuse_one_record");
            } finally {
                sql.execute("alter table audit_events drop constraint refuse_one_record");
            }
        }

        // Neither the update nor the delete happened.
        doGet(ACME_REP_RITA, record)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName", is("Original")))
                .andExpect(jsonPath("$.version", is(0)));
        assertThat(eventCount("CONTACT", id)).isEqualTo(1);
    }

    // --- Who can read a history ---

    @Test
    void historyOfALiveRecordIsVisibleToItsOrganizationOnly() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        String history = "/api/contacts/" + id + "/history";

        doGet(ACME_REP_RITA, history).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        doGet(ACME_REP_RAVI, history).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        doGet(ACME_MANAGER, history).andExpect(status().isOk());

        // Another organization: 404, the same answer as for an id that does not exist.
        doGet(GLOBEX_ADMIN, history).andExpect(status().isNotFound());
        doGet(GLOBEX_REP, history).andExpect(status().isNotFound());
        doGet(ACME_ADMIN, "/api/contacts/999999/history").andExpect(status().isNotFound());
        // The id of a contact is not the id of a lead.
        doGet(ACME_ADMIN, "/api/opportunities/" + id + "/history").andExpect(status().isNotFound());
    }

    @Test
    void historyOfADeletedRecordIsForManagersAndAdminsOnly() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        String history = "/api/contacts/" + id + "/history";
        doDelete(ACME_ADMIN, "/api/contacts/" + id).andExpect(status().isNoContent());

        doGet(ACME_ADMIN, history).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        doGet(ACME_MANAGER, history).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        // Reps, even the former owner, get the same 404 as the record itself now gives.
        doGet(ACME_REP_RITA, history).andExpect(status().isNotFound());
        doGet(ACME_REP_RAVI, history).andExpect(status().isNotFound());
        // Another organization's admin gets nothing either.
        doGet(GLOBEX_ADMIN, history).andExpect(status().isNotFound());
    }

    @Test
    void historyIsNewestFirstAndPaged() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        String record = "/api/contacts/" + id;
        for (String name : new String[] {"One", "Two", "Three"}) {
            doPatch(ACME_REP_RITA, record, "{\"lastName\":\"" + name + "\"}").andExpect(status().isOk());
        }

        doGet(ACME_REP_RITA, record + "/history?size=2")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].changes.lastName.to", is("Three")))
                .andExpect(jsonPath("$[1].changes.lastName.to", is("Two")));
        // A sort order in the request is ignored: the history is always newest first.
        doGet(ACME_REP_RITA, record + "/history?size=2&page=1&sort=id,asc")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].changes.lastName.to", is("One")))
                .andExpect(jsonPath("$[1].action", is("CREATE")));
    }

    // --- The database's own rules, tested with raw SQL as the application's role ---

    @Test
    void rowLevelSecurityHidesOtherOrganizationsEvents() throws Exception {
        long acme = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        long globex = create(GLOBEX_REP, "/api/contacts", CONTACT);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // No organization set: nothing is visible.
        assertThat(jdbc.queryForObject("select count(*) from audit_events", Long.class)).isZero();

        // A Globex user whose query forgot its "where org_id = ?" clause.
        CurrentUserContext.set(new CurrentUser(5L, 2L, Role.ADMIN));
        try {
            tx.executeWithoutResult(status -> {
                assertThat(jdbc.queryForObject(
                        "select count(*) from audit_events where entity_id = " + globex, Long.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "select count(*) from audit_events where entity_id = " + acme, Long.class)).isZero();
                assertThat(jdbc.queryForObject(
                        "select count(*) from audit_events where org_id <> 2", Long.class)).isZero();
            });
            // ...and it cannot plant an event in Acme's history.
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc.update(
                    "insert into audit_events (org_id, actor_user_id, entity_type, entity_id, action, changes, "
                            + "version_after, created_at) values (1, 1, 'CONTACT', " + acme
                            + ", 'UPDATE', '{}'::jsonb, 1, now())")))
                    .rootCause()
                    .hasMessageContaining("violates row-level security policy");
        } finally {
            CurrentUserContext.clear();
        }
    }

    @Test
    void theApplicationRoleCannotChangeOrRemoveEvents() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        CurrentUserContext.set(new CurrentUser(1L, 1L, Role.ADMIN));
        try {
            for (String sql : new String[] {
                    "update audit_events set actor_user_id = 2 where entity_id = " + id,
                    "delete from audit_events where entity_id = " + id,
                    "truncate audit_events"}) {
                assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc.update(sql)))
                        .as(sql)
                        .rootCause()
                        .hasMessageContaining("permission denied for table audit_events");
            }
        } finally {
            CurrentUserContext.clear();
        }
        assertThat(eventCount("CONTACT", id)).isEqualTo(1);
    }

    /**
     * The migrations' role owns the table (and in this test database is also a
     * superuser), so no grant stops it. The trigger does.
     */
    @Test
    void evenTheOwnerRoleCannotChangeOrRemoveEvents() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", CONTACT);

        try (Connection owner = ownerConnection(); Statement sql = owner.createStatement()) {
            for (String statement : new String[] {
                    "update audit_events set actor_user_id = 2 where entity_id = " + id,
                    "delete from audit_events where entity_id = " + id,
                    // A statement that matches no row is refused too.
                    "delete from audit_events where id = -1",
                    "truncate audit_events"}) {
                assertThatThrownBy(() -> sql.execute(statement))
                        .as(statement)
                        .hasMessageContaining("audit_events is append-only");
            }
        }
        assertThat(eventCount("CONTACT", id)).isEqualTo(1);
    }

    // --- Counting events directly in the database, as the owner (not limited by row-level security) ---

    private static long eventCount() throws Exception {
        return count("select count(*) from audit_events");
    }

    private static long eventCount(String entityType, long entityId) throws Exception {
        return count("select count(*) from audit_events where entity_type = '" + entityType
                + "' and entity_id = " + entityId);
    }

    private static long count(String query) throws Exception {
        try (Connection owner = ownerConnection();
             Statement sql = owner.createStatement();
             ResultSet rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
