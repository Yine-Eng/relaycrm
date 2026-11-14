package com.relaycrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.CurrentUserContext;
import com.relaycrm.auth.Role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** No call may read or change another organization's records, even with a valid id. */
class TenantIsolationTest extends IntegrationTestBase {

    @Autowired
    DataSource dataSource;

    @Autowired
    PlatformTransactionManager transactionManager;

    // In the demo data, record 1 of each type belongs to Acme and the given "other" id to Globex.

    @Test
    void requestWithoutValidApiKeyIsRejected() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/contacts"))
                .andExpect(status().isUnauthorized());
        doGet("not-a-real-key", "/api/contacts").andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"contacts", "leads", "opportunities"})
    void listShowsOnlyTheCallersOrganization(String type) throws Exception {
        doGet(ACME_REP_RITA, "/api/" + type)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orgId", everyItem(is(1))));
        doGet(GLOBEX_REP, "/api/" + type)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orgId", everyItem(is(2))));
    }

    @ParameterizedTest
    @CsvSource({"contacts", "leads", "opportunities"})
    void anotherOrganizationsRecordCannotBeRead(String type) throws Exception {
        doGet(ACME_ADMIN, "/api/" + type + "/1").andExpect(status().isOk());
        // Globex's admin guesses Acme's id 1: same answer as for an id that does not exist.
        doGet(GLOBEX_ADMIN, "/api/" + type + "/1").andExpect(status().isNotFound());
        doGet(GLOBEX_ADMIN, "/api/" + type + "/" + unusedId(type)).andExpect(status().isNotFound());
    }

    @Test
    void anotherOrganizationsRecordCannotBeUpdatedOrDeleted() throws Exception {
        doPut(GLOBEX_ADMIN, "/api/contacts/1", contactJson("Hijacked")).andExpect(status().isNotFound());
        doDelete(GLOBEX_ADMIN, "/api/contacts/1").andExpect(status().isNotFound());
        doPut(GLOBEX_ADMIN, "/api/leads/1", "{\"name\":\"Hijacked\"}").andExpect(status().isNotFound());
        doDelete(GLOBEX_ADMIN, "/api/leads/1").andExpect(status().isNotFound());
        doPut(GLOBEX_ADMIN, "/api/opportunities/1", "{\"name\":\"Hijacked\"}").andExpect(status().isNotFound());
        doDelete(GLOBEX_ADMIN, "/api/opportunities/1").andExpect(status().isNotFound());

        // Acme's records are untouched.
        doGet(ACME_ADMIN, "/api/contacts/1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName", not("Hijacked")));
        doGet(ACME_ADMIN, "/api/leads/1").andExpect(jsonPath("$.name", not("Hijacked")));
        doGet(ACME_ADMIN, "/api/opportunities/1").andExpect(jsonPath("$.name", not("Hijacked")));
    }

    @Test
    void newRecordAlwaysLandsInTheCallersOrganization() throws Exception {
        // The organization comes from the authenticated user...
        doPost(GLOBEX_REP, "/api/contacts", "{\"firstName\":\"A\",\"lastName\":\"B\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orgId", is(2)))
                .andExpect(jsonPath("$.ownerId", is(6)));
        // ...and a body that tries to name one is rejected outright.
        doPost(GLOBEX_REP, "/api/contacts", "{\"firstName\":\"A\",\"lastName\":\"B\",\"orgId\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.orgId", is("unknown field")));
    }

    @Test
    void anotherOrganizationsRecordCannotBePatched() throws Exception {
        doPatch(GLOBEX_ADMIN, "/api/contacts/1", "{\"lastName\":\"Hijacked\"}").andExpect(status().isNotFound());
        doPatch(GLOBEX_ADMIN, "/api/leads/1", "{\"name\":\"Hijacked\"}").andExpect(status().isNotFound());
        doPatch(GLOBEX_ADMIN, "/api/opportunities/1", "{\"name\":\"Hijacked\"}").andExpect(status().isNotFound());

        doGet(ACME_ADMIN, "/api/contacts/1").andExpect(jsonPath("$.lastName", not("Hijacked")));
        doGet(ACME_ADMIN, "/api/leads/1").andExpect(jsonPath("$.name", not("Hijacked")));
        doGet(ACME_ADMIN, "/api/opportunities/1").andExpect(jsonPath("$.name", not("Hijacked")));
    }

    @Test
    void recordCannotBeAssignedToAUserOfAnotherOrganization() throws Exception {
        // User 5 is Globex's admin.
        doPost(ACME_ADMIN, "/api/contacts", "{\"firstName\":\"A\",\"lastName\":\"B\",\"ownerId\":5}")
                .andExpect(status().isBadRequest());
        doPut(ACME_ADMIN, "/api/contacts/1", "{\"firstName\":\"A\",\"lastName\":\"B\",\"ownerId\":5}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void opportunityCannotLinkToAContactOfAnotherOrganization() throws Exception {
        // Contact 3 belongs to Globex.
        doPost(ACME_ADMIN, "/api/opportunities", "{\"name\":\"Deal\",\"contactId\":3}")
                .andExpect(status().isBadRequest());
        doPost(ACME_ADMIN, "/api/opportunities", "{\"name\":\"Deal\",\"contactId\":1}")
                .andExpect(status().isCreated());
    }

    // --- Second line of defence: PostgreSQL row-level security, tested by bypassing the service layer ---

    @Test
    void databaseHidesEverythingWhenNoOrganizationIsSet() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("select count(*) from contacts", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from leads", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from opportunities", Long.class)).isZero();
    }

    @Test
    void databaseItselfBlocksCrossOrganizationQueriesAndWrites() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // Pretend a buggy code path, running as an Acme user, forgot its "where org_id = ?" clause.
        CurrentUserContext.set(new CurrentUser(1L, 1L, Role.ADMIN));
        try {
            tx.executeWithoutResult(status -> {
                assertThat(jdbc.queryForObject("select count(*) from contacts where org_id = 1", Long.class))
                        .isPositive();
                assertThat(jdbc.queryForObject("select count(*) from contacts where org_id <> 1", Long.class))
                        .isZero();
                assertThat(jdbc.update("update contacts set last_name = 'Hijacked' where id = 3")).isZero();
                assertThat(jdbc.update("delete from contacts where id = 3")).isZero();
            });
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc.update(
                    "insert into contacts (org_id, owner_id, first_name, last_name, created_at, updated_at) "
                            + "values (2, 6, 'Planted', 'Row', now(), now())")))
                    .rootCause()
                    .hasMessageContaining("violates row-level security policy");
        } finally {
            CurrentUserContext.clear();
        }
    }
}
