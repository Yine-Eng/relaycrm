package com.relaycrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Deal stages and lead statuses may only change along the allowed moves.
 * The tables below are written out here on purpose, independently of the
 * enums, and every from/to pair is tried against the real database: each
 * allowed move must succeed and every other move must be a 409.
 */
class StageTransitionTest extends IntegrationTestBase {

    private static final List<String> STAGES = List.of("PROSPECTING", "PROPOSAL", "NEGOTIATION", "WON", "LOST");
    private static final Map<String, List<String>> STAGE_MOVES = Map.of(
            "PROSPECTING", List.of("PROPOSAL", "LOST"),
            "PROPOSAL", List.of("PROSPECTING", "NEGOTIATION", "WON", "LOST"),
            "NEGOTIATION", List.of("PROPOSAL", "WON", "LOST"),
            "WON", List.of(),
            "LOST", List.of());

    private static final List<String> STATUSES = List.of("NEW", "CONTACTED", "QUALIFIED", "DISQUALIFIED");
    private static final Map<String, List<String>> STATUS_MOVES = Map.of(
            "NEW", List.of("CONTACTED", "QUALIFIED", "DISQUALIFIED"),
            "CONTACTED", List.of("QUALIFIED", "DISQUALIFIED"),
            "QUALIFIED", List.of("CONTACTED", "DISQUALIFIED"),
            "DISQUALIFIED", List.of("CONTACTED"));

    static Stream<Arguments> everyStagePair() {
        return STAGES.stream().flatMap(from -> STAGES.stream().map(to -> Arguments.of(from, to)));
    }

    static Stream<Arguments> everyStatusPair() {
        return STATUSES.stream().flatMap(from -> STATUSES.stream().map(to -> Arguments.of(from, to)));
    }

    // --- Every pair, for deals and for leads ---

    @ParameterizedTest(name = "deal {0} -> {1}")
    @MethodSource("everyStagePair")
    void everyDealStageMoveIsAllowedOrRefusedAsTheTableSays(String from, String to) throws Exception {
        assertMove("/api/opportunities", "stage", from, to, STAGE_MOVES.get(from));
    }

    @ParameterizedTest(name = "lead {0} -> {1}")
    @MethodSource("everyStatusPair")
    void everyLeadStatusMoveIsAllowedOrRefusedAsTheTableSays(String from, String to) throws Exception {
        assertMove("/api/leads", "status", from, to, STATUS_MOVES.get(from));
    }

    private void assertMove(String path, String field, String from, String to, List<String> allowed)
            throws Exception {
        // A new record may start anywhere: creating is not a move.
        String record = path + "/" + create(ACME_REP_RITA, path, "{\"name\":\"Deal\",\"" + field + "\":\"" + from + "\"}");
        String patch = "{\"" + field + "\":\"" + to + "\"}";

        if (from.equals(to)) {
            // The same value again is not a move: accepted, and nothing changes.
            doPatch(ACME_REP_RITA, record, patch)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$." + field, is(from)))
                    .andExpect(jsonPath("$.version", is(0)));
        } else if (allowed.contains(to)) {
            doPatch(ACME_REP_RITA, record, patch)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$." + field, is(to)))
                    .andExpect(jsonPath("$.version", is(1)));
        } else {
            doPatch(ACME_REP_RITA, record, patch)
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status", is(409)))
                    .andExpect(jsonPath("$.field", is(field)))
                    .andExpect(jsonPath("$.from", is(from)))
                    .andExpect(jsonPath("$.to", is(to)))
                    .andExpect(jsonPath("$.allowed", is(allowed)));
            // The record is untouched.
            doGet(ACME_REP_RITA, record)
                    .andExpect(jsonPath("$." + field, is(from)))
                    .andExpect(jsonPath("$.version", is(0)));
        }
    }

    // --- WON and LOST are final ---

    @ParameterizedTest
    @ValueSource(strings = {"WON", "LOST"})
    void nothingMovesOutOfAClosedDeal(String closed) throws Exception {
        String deal = "/api/opportunities/" + create(ACME_REP_RITA, "/api/opportunities",
                "{\"name\":\"Closed deal\",\"stage\":\"" + closed + "\"}");

        for (String to : STAGES) {
            if (to.equals(closed)) {
                continue;
            }
            // Not for the owner, not for a manager, not for an admin; not by PATCH and not by PUT.
            doPatch(ACME_REP_RITA, deal, "{\"stage\":\"" + to + "\"}").andExpect(status().isConflict());
            doPatch(ACME_MANAGER, deal, "{\"stage\":\"" + to + "\"}").andExpect(status().isConflict());
            doPut(ACME_ADMIN, deal, "{\"name\":\"Closed deal\",\"stage\":\"" + to + "\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.allowed", is(List.of())));
        }
        // Other fields of a closed deal can still be edited.
        doPatch(ACME_REP_RITA, deal, "{\"amount\":100}").andExpect(status().isOk());
        doGet(ACME_REP_RITA, deal).andExpect(jsonPath("$.stage", is(closed)));
    }

    // --- A refused move changes nothing and leaves no trace ---

    @Test
    void aRefusedMoveSavesNothingAndWritesNoAuditEvent() throws Exception {
        long id = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Deal\",\"amount\":10}");
        String deal = "/api/opportunities/" + id;

        // The move is refused, so the amount sent along with it is not saved either.
        doPatch(ACME_REP_RITA, deal, "{\"stage\":\"WON\",\"amount\":99}").andExpect(status().isConflict());

        doGet(ACME_REP_RITA, deal)
                .andExpect(jsonPath("$.stage", is("PROSPECTING")))
                .andExpect(jsonPath("$.amount", is(10.0)))
                .andExpect(jsonPath("$.version", is(0)));
        assertThat(ownerQueryForLong("select count(*) from audit_events where entity_type = 'OPPORTUNITY' "
                + "and entity_id = " + id)).isEqualTo(1);   // only the CREATE
    }

    // --- Where the check sits among the others ---

    @Test
    void accessVersionAndBodyAreCheckedBeforeTheMove() throws Exception {
        String deal = "/api/opportunities/" + create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Deal\"}");
        String forbiddenMove = "{\"stage\":\"WON\"}";

        doPatch(GLOBEX_ADMIN, deal, forbiddenMove).andExpect(status().isNotFound());
        doPatch(ACME_REP_RAVI, deal, forbiddenMove).andExpect(status().isForbidden());
        doPatch(ACME_REP_RITA, deal, null, forbiddenMove).andExpect(status().isPreconditionRequired());
        doPatch(ACME_REP_RITA, deal, "\"99\"", forbiddenMove).andExpect(status().isPreconditionFailed());
        doPatch(ACME_REP_RITA, deal, "{\"stage\":\"WON\",\"name\":null}").andExpect(status().isBadRequest());
        doPatch(ACME_REP_RITA, deal, "{\"stage\":\"NOT_A_STAGE\"}").andExpect(status().isBadRequest());
        // With all of that in order, the move itself is what is refused.
        doPatch(ACME_REP_RITA, deal, forbiddenMove).andExpect(status().isConflict());
    }

    @Test
    void aDealWalksTheWholePipeline() throws Exception {
        String deal = "/api/opportunities/" + create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Deal\"}");
        for (String next : List.of("PROPOSAL", "NEGOTIATION", "PROPOSAL", "NEGOTIATION", "WON")) {
            doPatch(ACME_REP_RITA, deal, "{\"stage\":\"" + next + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stage", is(next)));
        }
        doGet(ACME_REP_RITA, deal).andExpect(jsonPath("$.version", is(5)));
    }
}
