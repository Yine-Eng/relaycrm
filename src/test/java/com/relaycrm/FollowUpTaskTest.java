package com.relaycrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.CurrentUserContext;
import com.relaycrm.auth.Role;
import com.relaycrm.task.FollowUpTasks;
import com.relaycrm.task.TaskLink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Follow-up tasks: created automatically when a deal or a lead reaches a
 * stage, never twice while one is open, always in the same transaction as
 * the stage change, and with the same isolation and role rules as every
 * other record. Runs against a real PostgreSQL, with the clock fixed so
 * that due dates can be checked exactly.
 */
class FollowUpTaskTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2027-01-10T09:00:00Z");
    private static final String IN_ONE_DAY = "2027-01-11T09:00:00Z";
    private static final String IN_THREE_DAYS = "2027-01-13T09:00:00Z";
    private static final String DUE = "\"dueAt\":\"2027-02-01T12:00:00Z\"";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    FollowUpTasks followUps;

    @Autowired
    DataSource dataSource;

    @Autowired
    PlatformTransactionManager transactionManager;

    // --- Automatic creation ---

    @Test
    void enteringProposalCreatesExactlyOneTaskForTheDealsOwnerDueInThreeDays() throws Exception {
        long deal = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Anvil contract\"}");   // Rita is user 3
        doGet(ACME_MANAGER, "/api/tasks?opportunityId=" + deal).andExpect(jsonPath("$", hasSize(0)));

        // The manager (user 2) moves Rita's deal: the task still goes to Rita, the deal's owner.
        doPatch(ACME_MANAGER, "/api/opportunities/" + deal, "{\"stage\":\"PROPOSAL\"}").andExpect(status().isOk());

        doGet(ACME_MANAGER, "/api/tasks?opportunityId=" + deal)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Follow up on proposal: Anvil contract")))
                .andExpect(jsonPath("$[0].ownerId", is(3)))
                .andExpect(jsonPath("$[0].dueAt", is(IN_THREE_DAYS)))
                .andExpect(jsonPath("$[0].status", is("OPEN")))
                .andExpect(jsonPath("$[0].source", is("AUTO")))
                .andExpect(jsonPath("$[0].opportunityId", is((int) deal)))
                .andExpect(jsonPath("$[0].leadId", nullValue()))
                .andExpect(jsonPath("$[0].dedupeKey", is("opportunity:" + deal + ":proposal-follow-up")))
                .andExpect(jsonPath("$[0].version", is(0)));

        // The task's own history says who caused it: the manager.
        doGet(ACME_REP_RITA, "/api/tasks/" + onlyTask("opportunityId", deal) + "/history")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action", is("CREATE")))
                .andExpect(jsonPath("$[0].actorUserId", is(2)))
                .andExpect(jsonPath("$[0].entityType", is("TASK")));
    }

    @Test
    void aDealCreatedDirectlyInProposalGetsItsTaskToo() throws Exception {
        long deal = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Straight in\",\"stage\":\"PROPOSAL\"}");
        doGet(ACME_REP_RITA, "/api/tasks?opportunityId=" + deal)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].dueAt", is(IN_THREE_DAYS)));
    }

    @Test
    void aQualifiedLeadGetsAContactTaskDueInOneDay() throws Exception {
        long moved = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Lena\"}");
        doPatch(ACME_REP_RITA, "/api/leads/" + moved, "{\"status\":\"QUALIFIED\"}").andExpect(status().isOk());
        long createdQualified = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Quentin\",\"status\":\"QUALIFIED\"}");

        doGet(ACME_REP_RITA, "/api/tasks?leadId=" + moved)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Contact qualified lead: Lena")))
                .andExpect(jsonPath("$[0].dueAt", is(IN_ONE_DAY)))
                .andExpect(jsonPath("$[0].ownerId", is(3)))
                .andExpect(jsonPath("$[0].source", is("AUTO")))
                .andExpect(jsonPath("$[0].leadId", is((int) moved)))
                .andExpect(jsonPath("$[0].opportunityId", nullValue()));
        doGet(ACME_REP_RITA, "/api/tasks?leadId=" + createdQualified)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Contact qualified lead: Quentin")));
    }

    @Test
    void otherStagesCreateNoTask() throws Exception {
        long deal = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Quiet\",\"stage\":\"NEGOTIATION\"}");
        doPatch(ACME_REP_RITA, "/api/opportunities/" + deal, "{\"amount\":5}").andExpect(status().isOk());
        long lead = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Quiet\"}");
        doPatch(ACME_REP_RITA, "/api/leads/" + lead, "{\"status\":\"CONTACTED\"}").andExpect(status().isOk());

        assertThat(ownerQueryForLong("select count(*) from tasks where opportunity_id = " + deal
                + " or lead_id = " + lead)).isZero();
    }

    // --- Duplicate prevention ---

    @Test
    void reEnteringProposalWhileTheTaskIsOpenCreatesNoneAndAfterDoneCreatesANewOne() throws Exception {
        long id = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Back and forth\"}");
        String deal = "/api/opportunities/" + id;

        move(deal, "PROPOSAL");
        long first = onlyTask("opportunityId", id);

        // PROPOSAL -> NEGOTIATION -> PROPOSAL while the first task is still open: still one task.
        move(deal, "NEGOTIATION");
        move(deal, "PROPOSAL");
        doGet(ACME_REP_RITA, "/api/tasks?opportunityId=" + id)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is((int) first)));

        // Rita completes it. Entering PROPOSAL again now creates a new one.
        doPatch(ACME_REP_RITA, "/api/tasks/" + first, "{\"status\":\"DONE\"}").andExpect(status().isOk());
        move(deal, "NEGOTIATION");
        move(deal, "PROPOSAL");
        doGet(ACME_REP_RITA, "/api/tasks?opportunityId=" + id).andExpect(jsonPath("$", hasSize(2)));
        doGet(ACME_REP_RITA, "/api/tasks?opportunityId=" + id + "&status=OPEN")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", not((int) first)));
        doGet(ACME_REP_RITA, "/api/tasks?opportunityId=" + id + "&status=DONE")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is((int) first)));

        // And once more while the second is open: nothing new.
        move(deal, "NEGOTIATION");
        move(deal, "PROPOSAL");
        assertThat(ownerQueryForLong("select count(*) from tasks where opportunity_id = " + id)).isEqualTo(2);
    }

    /** The rule lives in the database: even a direct INSERT cannot create a second open task for a key. */
    @Test
    void theDatabaseItselfRefusesASecondOpenTaskWithTheSameKey() throws Exception {
        String key = "test:duplicate:" + System.nanoTime();
        String insert = "insert into tasks (org_id, owner_id, title, due_at, status, source, dedupe_key, "
                + "created_at, updated_at) values (1, 3, 'Duplicate?', now(), '%s', 'AUTO', '" + key + "', now(), now())";

        try (Connection owner = ownerConnection(); Statement sql = owner.createStatement()) {
            sql.execute(insert.formatted("OPEN"));
            assertThatThrownBy(() -> sql.execute(insert.formatted("OPEN")))
                    .hasMessageContaining("tasks_one_open_per_key");
            // Finished tasks do not count: any number of DONE ones may share the key.
            sql.execute(insert.formatted("DONE"));
            sql.execute(insert.formatted("DONE"));
            // Another organization has its own keys.
            sql.execute(insert.formatted("OPEN").replace("(1, 3,", "(2, 6,"));
        }
        assertThat(ownerQueryForLong("select count(*) from tasks where dedupe_key = '" + key
                + "' and status = 'OPEN' and org_id = 1")).isEqualTo(1);
    }

    /**
     * A forced race. Through the API two stage changes of one deal can never
     * insert at the same moment (the deal's version check puts them in a row),
     * so this test calls the insert directly from two transactions.
     *
     * The first inserts and keeps its transaction open. The second then tries
     * the same key and has to wait: PostgreSQL does not yet know whether the
     * first will commit. Only when PostgreSQL reports the second as waiting does
     * the test let the first one finish.
     */
    @ParameterizedTest(name = "first transaction commits: {0}")
    @ValueSource(booleans = {true, false})
    void twoTransactionsInsertingTheSameKeyAtOnceLeaveExactlyOneOpenTask(boolean firstCommits) throws Exception {
        long deal = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Raced\"}");
        String rule = "race-" + firstCommits;
        CurrentUser rita = new CurrentUser(3L, 1L, Role.REP);
        CountDownLatch firstHasInserted = new CountDownLatch(1);
        CountDownLatch letFirstFinish = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try (Connection owner = ownerConnection(); Statement sql = owner.createStatement()) {
            Future<Optional<Long>> first = pool.submit(() -> inTransaction(rita, status -> {
                Optional<Long> id = insertFollowUp(rita, deal, rule);
                firstHasInserted.countDown();
                await(letFirstFinish);
                if (!firstCommits) {
                    status.setRollbackOnly();
                }
                return id;
            }));
            assertThat(firstHasInserted.await(20, TimeUnit.SECONDS)).isTrue();

            Future<Optional<Long>> second = pool.submit(() -> inTransaction(rita, status -> insertFollowUp(rita, deal, rule)));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (ownerQueryForLong("select count(*) from pg_stat_activity "
                    + "where datname = current_database() and wait_event_type = 'Lock'") < 1) {
                assertThat(System.nanoTime()).as("the second insert should be waiting for the first").isLessThan(deadline);
                Thread.sleep(50);
            }
            assertThat(second.isDone()).isFalse();
            letFirstFinish.countDown();

            assertThat(first.get(20, TimeUnit.SECONDS)).isPresent();
            // First committed: the second inserts nothing. First rolled back: the second's insert goes in.
            assertThat(second.get(20, TimeUnit.SECONDS).isPresent()).isEqualTo(!firstCommits);
        } finally {
            letFirstFinish.countDown();
            pool.shutdownNow();
        }

        String key = "opportunity:" + deal + ":" + rule;
        assertThat(ownerQueryForLong("select count(*) from tasks where dedupe_key = '" + key + "'")).isEqualTo(1);
        assertThat(ownerQueryForLong("select count(*) from tasks where dedupe_key = '" + key
                + "' and status = 'OPEN'")).isEqualTo(1);
        // One task, one CREATE event: the insert that was skipped (or rolled back) left no audit event.
        assertThat(ownerQueryForLong("select count(*) from audit_events e join tasks t on t.id = e.entity_id "
                + "where e.entity_type = 'TASK' and t.dedupe_key = '" + key + "'")).isEqualTo(1);
    }

    // --- One transaction: the stage change and its task are saved together or not at all ---

    @Test
    void aFailedTaskInsertRollsBackTheStageChange() throws Exception {
        long id = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"All or nothing\"}");
        String deal = "/api/opportunities/" + id;

        // Make the database refuse any task for this one deal.
        try (Connection owner = ownerConnection(); Statement sql = owner.createStatement()) {
            sql.execute("alter table tasks add constraint refuse_one_deal "
                    + "check (opportunity_id is distinct from " + id + ") not valid");
            try {
                assertThatThrownBy(() -> doPatch(ACME_REP_RITA, deal, "\"0\"", "{\"stage\":\"PROPOSAL\"}"))
                        .rootCause()
                        .hasMessageContaining("refuse_one_deal");
            } finally {
                sql.execute("alter table tasks drop constraint refuse_one_deal");
            }
        }

        // The deal did not move, and nothing was recorded.
        doGet(ACME_REP_RITA, deal)
                .andExpect(jsonPath("$.stage", is("PROSPECTING")))
                .andExpect(jsonPath("$.version", is(0)));
        assertThat(ownerQueryForLong("select count(*) from tasks where opportunity_id = " + id)).isZero();
        assertThat(ownerQueryForLong("select count(*) from audit_events where entity_type = 'OPPORTUNITY' "
                + "and entity_id = " + id)).isEqualTo(1);   // only the CREATE

        // With the obstacle gone, the same request works and creates the task.
        doPatch(ACME_REP_RITA, deal, "\"0\"", "{\"stage\":\"PROPOSAL\"}").andExpect(status().isOk());
        assertThat(ownerQueryForLong("select count(*) from tasks where opportunity_id = " + id)).isEqualTo(1);
    }

    // --- Tasks follow their deal or lead ---

    @Test
    void closingADealCancelsItsOpenAutomaticTasksButNotManualOnes() throws Exception {
        for (String closed : List.of("WON", "LOST")) {
            long id = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Closing\",\"stage\":\"PROPOSAL\"}");
            long automatic = onlyTask("opportunityId", id);
            long manual = create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Send thank-you card\"," + DUE
                    + ",\"opportunityId\":" + id + "}");

            doPatch(ACME_MANAGER, "/api/opportunities/" + id, "{\"stage\":\"" + closed + "\"}")
                    .andExpect(status().isOk());

            doGet(ACME_REP_RITA, "/api/tasks/" + automatic).andExpect(jsonPath("$.status", is("CANCELLED")));
            doGet(ACME_REP_RITA, "/api/tasks/" + manual).andExpect(jsonPath("$.status", is("OPEN")));
            // Audited, with the person who closed the deal as the actor.
            doGet(ACME_REP_RITA, "/api/tasks/" + automatic + "/history")
                    .andExpect(jsonPath("$[0].action", is("UPDATE")))
                    .andExpect(jsonPath("$[0].actorUserId", is(2)))
                    .andExpect(jsonPath("$[0].changes.status.from", is("OPEN")))
                    .andExpect(jsonPath("$[0].changes.status.to", is("CANCELLED")));
        }
    }

    @Test
    void disqualifyingALeadCancelsItsOpenAutomaticTasks() throws Exception {
        long lead = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Dana\",\"status\":\"QUALIFIED\"}");
        long task = onlyTask("leadId", lead);

        doPatch(ACME_REP_RITA, "/api/leads/" + lead, "{\"status\":\"DISQUALIFIED\"}").andExpect(status().isOk());

        doGet(ACME_REP_RITA, "/api/tasks/" + task).andExpect(jsonPath("$.status", is("CANCELLED")));
        doGet(ACME_REP_RITA, "/api/tasks/" + task + "/history").andExpect(jsonPath("$[0].actorUserId", is(3)));
    }

    @Test
    void whenADealChangesOwnerItsOpenAutomaticTasksMoveToTheNewOwner() throws Exception {
        long id = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Handed over\",\"stage\":\"PROPOSAL\"}");
        long automatic = onlyTask("opportunityId", id);
        long manual = create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Rita's own note\"," + DUE
                + ",\"opportunityId\":" + id + "}");

        // The manager gives the deal to Ravi (user 4).
        doPatch(ACME_MANAGER, "/api/opportunities/" + id, "{\"ownerId\":4}").andExpect(status().isOk());

        doGet(ACME_MANAGER, "/api/tasks/" + automatic)
                .andExpect(jsonPath("$.ownerId", is(4)))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.version", is(1)));
        doGet(ACME_MANAGER, "/api/tasks/" + manual).andExpect(jsonPath("$.ownerId", is(3)));
        doGet(ACME_MANAGER, "/api/tasks/" + automatic + "/history")
                .andExpect(jsonPath("$[0].action", is("UPDATE")))
                .andExpect(jsonPath("$[0].actorUserId", is(2)))
                .andExpect(jsonPath("$[0].changes.ownerId.from", is(3)))
                .andExpect(jsonPath("$[0].changes.ownerId.to", is(4)));
        // Ravi now sees it in his list; Rita no longer does.
        doGet(ACME_REP_RAVI, "/api/tasks?opportunityId=" + id).andExpect(jsonPath("$[*].id", contains((int) automatic)));
        doGet(ACME_REP_RITA, "/api/tasks?opportunityId=" + id).andExpect(jsonPath("$[*].id", contains((int) manual)));

        // The same for a lead.
        long lead = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Lena\",\"status\":\"QUALIFIED\"}");
        doPatch(ACME_ADMIN, "/api/leads/" + lead, "{\"ownerId\":4}").andExpect(status().isOk());
        doGet(ACME_ADMIN, "/api/tasks?leadId=" + lead).andExpect(jsonPath("$[0].ownerId", is(4)));
    }

    @Test
    void deletingADealCancelsItsOpenTasksAndTheTasksSurviveInsideTheirOrganization() throws Exception {
        long id = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Doomed\",\"stage\":\"PROPOSAL\"}");
        long automatic = onlyTask("opportunityId", id);
        long manual = create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Call them\"," + DUE + ",\"opportunityId\":" + id + "}");
        long finished = create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Sent brochure\"," + DUE
                + ",\"opportunityId\":" + id + ",\"status\":\"DONE\"}");

        doDelete(ACME_ADMIN, "/api/opportunities/" + id).andExpect(status().isNoContent());   // Alice is user 1

        // Open tasks, automatic and manual, are cancelled; the finished one stays as it was.
        for (long task : List.of(automatic, manual)) {
            doGet(ACME_ADMIN, "/api/tasks/" + task)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CANCELLED")))
                    .andExpect(jsonPath("$.opportunityId", nullValue()))
                    .andExpect(jsonPath("$.orgId", is(1)));
            doGet(ACME_ADMIN, "/api/tasks/" + task + "/history")
                    .andExpect(jsonPath("$[0].actorUserId", is(1)))
                    .andExpect(jsonPath("$[0].changes.status.to", is("CANCELLED")));
        }
        doGet(ACME_ADMIN, "/api/tasks/" + finished)
                .andExpect(jsonPath("$.status", is("DONE")))
                .andExpect(jsonPath("$.opportunityId", nullValue()));

        // In the database: only the link was cleared, the organization is intact.
        String mine = "id in (" + automatic + ", " + manual + ", " + finished + ")";
        assertThat(ownerQueryForLong("select count(*) from tasks where " + mine
                + " and org_id = 1 and opportunity_id is null")).isEqualTo(3);

        // So the tasks are still invisible to another organization: through the API...
        doGet(GLOBEX_ADMIN, "/api/tasks/" + automatic).andExpect(status().isNotFound());
        doGet(GLOBEX_ADMIN, "/api/tasks/" + automatic + "/history").andExpect(status().isNotFound());
        doGet(GLOBEX_ADMIN, "/api/tasks").andExpect(jsonPath("$[*].id", not(hasItem((int) automatic))));
        // ...and under row-level security, for a query that forgot its organization filter.
        assertThat(countAsOrganization(2L, "select count(*) from tasks where " + mine)).isZero();
        assertThat(countAsOrganization(1L, "select count(*) from tasks where " + mine)).isEqualTo(3);
    }

    @Test
    void deletingALeadCancelsItsOpenTasks() throws Exception {
        long lead = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Gone\",\"status\":\"QUALIFIED\"}");
        long task = onlyTask("leadId", lead);

        doDelete(ACME_ADMIN, "/api/leads/" + lead).andExpect(status().isNoContent());

        doGet(ACME_ADMIN, "/api/tasks/" + task)
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.leadId", nullValue()));
    }

    // --- Tenant isolation ---

    @Test
    void tasksAreInvisibleAndUntouchableFromAnotherOrganization() throws Exception {
        long deal = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Private\",\"stage\":\"PROPOSAL\"}");
        long task = onlyTask("opportunityId", deal);
        String path = "/api/tasks/" + task;

        doGet(GLOBEX_ADMIN, path).andExpect(status().isNotFound());
        doGet(GLOBEX_ADMIN, path + "/history").andExpect(status().isNotFound());
        doPatch(GLOBEX_ADMIN, path, "\"0\"", "{\"status\":\"DONE\"}").andExpect(status().isNotFound());
        doDelete(GLOBEX_ADMIN, path).andExpect(status().isNotFound());
        doGet(GLOBEX_ADMIN, "/api/tasks").andExpect(jsonPath("$[*].orgId", everyItem(is(2))));
        doGet(GLOBEX_ADMIN, "/api/tasks?opportunityId=" + deal).andExpect(jsonPath("$", hasSize(0)));

        // A task cannot be linked to another organization's deal or lead, or given to its users.
        doPost(GLOBEX_ADMIN, "/api/tasks", "{\"title\":\"Sneaky\"," + DUE + ",\"opportunityId\":" + deal + "}")
                .andExpect(status().isBadRequest());
        doPost(GLOBEX_ADMIN, "/api/tasks", "{\"title\":\"Sneaky\"," + DUE + ",\"leadId\":1}")
                .andExpect(status().isBadRequest());
        doPost(GLOBEX_ADMIN, "/api/tasks", "{\"title\":\"Sneaky\"," + DUE + ",\"ownerId\":3}")
                .andExpect(status().isBadRequest());

        doGet(ACME_REP_RITA, path).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("OPEN")));
        assertThat(countAsOrganization(2L, "select count(*) from tasks where org_id <> 2")).isZero();
    }

    // --- Role rules ---

    @Test
    void aRepSeesOnlyTheirOwnTasksManagersAndAdminsSeeAll() throws Exception {
        long lead = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Shared lead\"}");
        long ritas = create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Rita's\"," + DUE + ",\"leadId\":" + lead + "}");
        long ravis = create(ACME_REP_RAVI, "/api/tasks", "{\"title\":\"Ravi's\"," + DUE + ",\"leadId\":" + lead + "}");

        doGet(ACME_REP_RITA, "/api/tasks?leadId=" + lead).andExpect(jsonPath("$[*].id", contains((int) ritas)));
        doGet(ACME_REP_RAVI, "/api/tasks?leadId=" + lead).andExpect(jsonPath("$[*].id", contains((int) ravis)));
        doGet(ACME_MANAGER, "/api/tasks?leadId=" + lead).andExpect(jsonPath("$[*].id", contains((int) ritas, (int) ravis)));
        doGet(ACME_ADMIN, "/api/tasks?leadId=" + lead).andExpect(jsonPath("$", hasSize(2)));
        // Without a filter too: a rep's list never contains anyone else's task.
        doGet(ACME_REP_RITA, "/api/tasks?size=500").andExpect(jsonPath("$[*].ownerId", everyItem(is(3))));

        // To a rep, another rep's task does not exist.
        doGet(ACME_REP_RAVI, "/api/tasks/" + ritas).andExpect(status().isNotFound());
        doGet(ACME_REP_RAVI, "/api/tasks/" + ritas + "/history").andExpect(status().isNotFound());
        doPatch(ACME_REP_RAVI, "/api/tasks/" + ritas, "\"0\"", "{\"status\":\"DONE\"}").andExpect(status().isNotFound());
        // Managers and admins can read and edit any task.
        doGet(ACME_MANAGER, "/api/tasks/" + ritas).andExpect(status().isOk());
        doPatch(ACME_MANAGER, "/api/tasks/" + ritas, "{\"title\":\"Renamed by the manager\"}").andExpect(status().isOk());
    }

    @Test
    void onlyManagersAndAdminsAssignTasksAndOnlyAdminsDeleteThem() throws Exception {
        doPost(ACME_REP_RITA, "/api/tasks", "{\"title\":\"For Ravi\"," + DUE + ",\"ownerId\":4}")
                .andExpect(status().isForbidden());
        long forRavi = create(ACME_MANAGER, "/api/tasks", "{\"title\":\"For Ravi\"," + DUE + ",\"ownerId\":4}");
        doGet(ACME_REP_RAVI, "/api/tasks/" + forRavi).andExpect(jsonPath("$.ownerId", is(4)));

        long ritas = create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Mine\"," + DUE + "}");
        doPatch(ACME_REP_RITA, "/api/tasks/" + ritas, "{\"ownerId\":4}").andExpect(status().isForbidden());

        doDelete(ACME_REP_RITA, "/api/tasks/" + ritas).andExpect(status().isForbidden());
        doDelete(ACME_MANAGER, "/api/tasks/" + ritas).andExpect(status().isForbidden());
        doDelete(ACME_ADMIN, "/api/tasks/" + ritas).andExpect(status().isNoContent());
        doGet(ACME_REP_RITA, "/api/tasks/" + ritas).andExpect(status().isNotFound());
        // Like any deleted record: its history stays readable for managers and admins.
        doGet(ACME_MANAGER, "/api/tasks/" + ritas + "/history")
                .andExpect(jsonPath("$[0].action", is("DELETE")))
                .andExpect(jsonPath("$[0].actorUserId", is(1)));
        doGet(ACME_REP_RITA, "/api/tasks/" + ritas + "/history").andExpect(status().isNotFound());
    }

    // --- The task API ---

    @Test
    void aManualTaskIsCreatedOpenAndCompletedWithIfMatch() throws Exception {
        doPost(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Call the customer\"," + DUE + "}")
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.source", is("MANUAL")))
                .andExpect(jsonPath("$.ownerId", is(3)))
                .andExpect(jsonPath("$.dueAt", is("2027-02-01T12:00:00Z")))
                .andExpect(jsonPath("$.dedupeKey", nullValue()));

        String task = "/api/tasks/" + create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Call the customer\"," + DUE + "}");

        doPatch(ACME_REP_RITA, task, null, "{\"status\":\"DONE\"}").andExpect(status().isPreconditionRequired());
        doPatch(ACME_REP_RITA, task, "\"7\"", "{\"status\":\"DONE\"}").andExpect(status().isPreconditionFailed());
        doPatch(ACME_REP_RITA, task, "\"0\"", "{\"status\":\"DONE\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.status", is("DONE")))
                .andExpect(jsonPath("$.title", is("Call the customer")));

        // A finished task is not reopened; its other fields can still be corrected.
        doPatch(ACME_REP_RITA, task, "{\"status\":\"OPEN\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field", is("status")))
                .andExpect(jsonPath("$.allowed", is(List.of())));
        doPatch(ACME_REP_RITA, task, "{\"status\":\"CANCELLED\"}").andExpect(status().isConflict());
        doPatch(ACME_REP_RITA, task, "{\"title\":\"Called the customer\"}").andExpect(status().isOk());
    }

    @Test
    void taskRequestsAreValidatedLikeEveryOtherRecord() throws Exception {
        long deal = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Deal\"}");
        long lead = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Lead\"}");

        doPost(ACME_REP_RITA, "/api/tasks", "{" + DUE + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title", is("must not be blank")));
        doPost(ACME_REP_RITA, "/api/tasks", "{\"title\":\"No date\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dueAt", is("must not be null")));
        doPost(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Both\"," + DUE + ",\"opportunityId\":" + deal
                + ",\"leadId\":" + lead + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.leadId", is("a task is about an opportunity or a lead, not both")));
        doPost(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Ghost\"," + DUE + ",\"opportunityId\":" + unusedId("opportunities") + "}")
                .andExpect(status().isBadRequest());
        // source and dedupeKey are decided by the server: sending them is an unknown field.
        doPost(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Fake\"," + DUE + ",\"source\":\"AUTO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.source", is("unknown field")));
        doPost(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Fake\"," + DUE + ",\"dedupeKey\":\"x\"}")
                .andExpect(status().isBadRequest());

        String task = "/api/tasks/" + create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Real\"," + DUE + "}");
        doPatch(ACME_REP_RITA, task, "{\"status\":null}").andExpect(status().isBadRequest());
        doPatch(ACME_REP_RITA, task, "{\"status\":\"PAUSED\"}").andExpect(status().isBadRequest());
        doGet(ACME_REP_RITA, "/api/tasks?status=PAUSED").andExpect(status().isBadRequest());
        // There is no PUT for tasks.
        doPut(ACME_REP_RITA, task, "{\"title\":\"Replaced\"," + DUE + ",\"status\":\"OPEN\"}")
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void theListIsSoonestDueFirstThenById() throws Exception {
        long lead = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Ordering\"}");
        String link = ",\"leadId\":" + lead + "}";
        long later = create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Later\",\"dueAt\":\"2027-03-03T10:00:00Z\"" + link);
        long soonA = create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Soon A\",\"dueAt\":\"2027-03-01T10:00:00Z\"" + link);
        long middle = create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Middle\",\"dueAt\":\"2027-03-02T10:00:00Z\"" + link);
        long soonB = create(ACME_REP_RITA, "/api/tasks", "{\"title\":\"Soon B\",\"dueAt\":\"2027-03-01T10:00:00Z\"" + link);

        doGet(ACME_REP_RITA, "/api/tasks?leadId=" + lead)
                .andExpect(jsonPath("$[*].id", contains((int) soonA, (int) soonB, (int) middle, (int) later)));
        // A sort order in the request is ignored; paging works.
        doGet(ACME_REP_RITA, "/api/tasks?leadId=" + lead + "&sort=id,desc&size=2&page=1")
                .andExpect(jsonPath("$[*].id", contains((int) middle, (int) later)));

        doPatch(ACME_REP_RITA, "/api/tasks/" + middle, "{\"status\":\"DONE\"}").andExpect(status().isOk());
        doGet(ACME_REP_RITA, "/api/tasks?leadId=" + lead + "&status=OPEN")
                .andExpect(jsonPath("$[*].id", contains((int) soonA, (int) soonB, (int) later)));
        doGet(ACME_REP_RITA, "/api/tasks?leadId=" + lead + "&status=DONE")
                .andExpect(jsonPath("$[*].id", contains((int) middle)));
    }

    // --- Helpers ---

    private void move(String deal, String stage) throws Exception {
        doPatch(ACME_REP_RITA, deal, "{\"stage\":\"" + stage + "\"}").andExpect(status().isOk());
    }

    /** The id of the one task linked to this deal or lead (fails if there is not exactly one). */
    private long onlyTask(String filter, long id) throws Exception {
        String body = doGet(ACME_ADMIN, "/api/tasks?" + filter + "=" + id).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids).hasSize(1);
        return ids.get(0).longValue();
    }

    private Optional<Long> insertFollowUp(CurrentUser actor, long deal, String rule) {
        return followUps.ensureOpen(actor, TaskLink.opportunity(deal), rule, "Raced task", actor.id(), Duration.ofDays(3));
    }

    /** Runs the work in its own transaction, as this user, on the calling thread. */
    private <T> T inTransaction(CurrentUser user,
                                org.springframework.transaction.support.TransactionCallback<T> work) {
        CurrentUserContext.set(user);
        try {
            return new TransactionTemplate(transactionManager).execute(work);
        } finally {
            CurrentUserContext.clear();
        }
    }

    /** Raw SQL as the application's role, with row-level security set to this organization. */
    private long countAsOrganization(long orgId, String query) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return inTransaction(new CurrentUser(orgId == 1 ? 1L : 5L, orgId, Role.ADMIN),
                status -> jdbc.queryForObject(query, Long.class));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
