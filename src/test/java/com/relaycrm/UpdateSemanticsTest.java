package com.relaycrm;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

/**
 * How records are written: PUT replaces, PATCH merges, and all three writing
 * verbs reject fields they do not know.
 */
class UpdateSemanticsTest extends IntegrationTestBase {

    private static final String FULL_CONTACT =
            "{\"firstName\":\"Nina\",\"lastName\":\"New\",\"email\":\"nina@fresh.test\","
                    + "\"phone\":\"555-0100\",\"company\":\"Fresh Co\"}";

    // --- PUT: full replacement ---

    @Test
    void putClearsOptionalFieldsThatAreLeftOut() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", FULL_CONTACT);
        doPut(ACME_REP_RITA, "/api/contacts/" + id, "{\"firstName\":\"Nina\",\"lastName\":\"Renamed\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName", is("Renamed")))
                .andExpect(jsonPath("$.email", nullValue()))
                .andExpect(jsonPath("$.phone", nullValue()))
                .andExpect(jsonPath("$.company", nullValue()));
    }

    @Test
    void putMissingARequiredFieldIsRejectedAndNamesTheField() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", FULL_CONTACT);
        doPut(ACME_REP_RITA, "/api/contacts/" + id, "{\"firstName\":\"Nina\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lastName", is("must not be blank")));
        // Nothing was changed.
        doGet(ACME_REP_RITA, "/api/contacts/" + id)
                .andExpect(jsonPath("$.lastName", is("New")))
                .andExpect(jsonPath("$.company", is("Fresh Co")));
    }

    // --- Lead status / opportunity stage: optional on create, required on update ---

    @Test
    void leadStatusDefaultsOnCreateButIsRequiredOnPut() throws Exception {
        doPost(ACME_REP_RITA, "/api/leads", "{\"name\":\"Lena\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("NEW")));

        long id = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Lena\",\"status\":\"QUALIFIED\"}");
        doPut(ACME_REP_RITA, "/api/leads/" + id, "{\"name\":\"Lena\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status", is("must not be null")));
        doGet(ACME_REP_RITA, "/api/leads/" + id).andExpect(jsonPath("$.status", is("QUALIFIED")));

        doPut(ACME_REP_RITA, "/api/leads/" + id, "{\"name\":\"Lena\",\"status\":\"CONTACTED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONTACTED")));
    }

    @Test
    void opportunityStageDefaultsOnCreateButIsRequiredOnPut() throws Exception {
        doPost(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Deal\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage", is("PROSPECTING")));

        long id = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Deal\",\"stage\":\"WON\"}");
        doPut(ACME_REP_RITA, "/api/opportunities/" + id, "{\"name\":\"Deal\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stage", is("must not be null")));
        doGet(ACME_REP_RITA, "/api/opportunities/" + id).andExpect(jsonPath("$.stage", is("WON")));
    }

    @Test
    void patchNullOnLeadStatusIsRejected() throws Exception {
        long id = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Lena\",\"status\":\"QUALIFIED\"}");
        doPatch(ACME_REP_RITA, "/api/leads/" + id, "{\"status\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status", is("must not be null")));
        doGet(ACME_REP_RITA, "/api/leads/" + id).andExpect(jsonPath("$.status", is("QUALIFIED")));
    }

    @Test
    void patchNullOnOpportunityStageIsRejected() throws Exception {
        long id = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Deal\",\"stage\":\"WON\"}");
        doPatch(ACME_REP_RITA, "/api/opportunities/" + id, "{\"stage\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stage", is("must not be null")));
        doGet(ACME_REP_RITA, "/api/opportunities/" + id).andExpect(jsonPath("$.stage", is("WON")));
    }

    // --- PATCH: JSON Merge Patch ---

    @Test
    void patchingOneFieldLeavesTheOthersUnchanged() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", FULL_CONTACT);
        doPatch(ACME_REP_RITA, "/api/contacts/" + id, "{\"company\":\"Renamed Co\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company", is("Renamed Co")))
                .andExpect(jsonPath("$.firstName", is("Nina")))
                .andExpect(jsonPath("$.lastName", is("New")))
                .andExpect(jsonPath("$.email", is("nina@fresh.test")))
                .andExpect(jsonPath("$.phone", is("555-0100")))
                .andExpect(jsonPath("$.ownerId", is(3)));

        long leadId = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Lena\",\"company\":\"Prospect Co\"}");
        doPatch(ACME_REP_RITA, "/api/leads/" + leadId, "{\"status\":\"QUALIFIED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("QUALIFIED")))
                .andExpect(jsonPath("$.name", is("Lena")))
                .andExpect(jsonPath("$.company", is("Prospect Co")));

        long oppId = create(ACME_REP_RITA, "/api/opportunities",
                "{\"name\":\"Deal\",\"contactId\":1,\"amount\":1200.50,\"closeDate\":\"2027-03-31\"}");
        doPatch(ACME_REP_RITA, "/api/opportunities/" + oppId, "{\"stage\":\"WON\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage", is("WON")))
                .andExpect(jsonPath("$.name", is("Deal")))
                .andExpect(jsonPath("$.contactId", is(1)))
                .andExpect(jsonPath("$.amount", is(1200.50)))
                .andExpect(jsonPath("$.closeDate", is("2027-03-31")));
    }

    @Test
    void patchWithNullClearsAnOptionalField() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", FULL_CONTACT);
        doPatch(ACME_REP_RITA, "/api/contacts/" + id, "{\"phone\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone", nullValue()))
                .andExpect(jsonPath("$.company", is("Fresh Co")));
    }

    @Test
    void patchWithNullOnARequiredFieldIsRejected() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", FULL_CONTACT);
        doPatch(ACME_REP_RITA, "/api/contacts/" + id, "{\"lastName\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lastName", is("must not be blank")));
        doGet(ACME_REP_RITA, "/api/contacts/" + id).andExpect(jsonPath("$.lastName", is("New")));
    }

    @Test
    void patchResultIsValidatedLikeAPut() throws Exception {
        long id = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Deal\",\"amount\":100}");
        doPatch(ACME_REP_RITA, "/api/opportunities/" + id, "{\"amount\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        doPatch(ACME_REP_RITA, "/api/opportunities/" + id, "{\"stage\":\"NOT_A_STAGE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stage", is("invalid value")));
        // Contact 3 belongs to another organization.
        doPatch(ACME_REP_RITA, "/api/opportunities/" + id, "{\"contactId\":3}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchRequiresTheMergePatchContentType() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", FULL_CONTACT);
        mvc.perform(patch("/api/contacts/" + id).header("X-Api-Key", ACME_REP_RITA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"x\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // --- PATCH follows the same permission rules as PUT ---

    @Test
    void repCannotPatchARecordOwnedBySomeoneElse() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", FULL_CONTACT);
        doPatch(ACME_REP_RAVI, "/api/contacts/" + id, "{\"company\":\"Ravis\"}").andExpect(status().isForbidden());
        doGet(ACME_REP_RITA, "/api/contacts/" + id).andExpect(jsonPath("$.company", is("Fresh Co")));

        doPatch(ACME_MANAGER, "/api/contacts/" + id, "{\"company\":\"By manager\"}").andExpect(status().isOk());
        doPatch(ACME_ADMIN, "/api/contacts/" + id, "{\"company\":\"By admin\"}").andExpect(status().isOk());
    }

    @Test
    void crossTenantPatchIsNotFound() throws Exception {
        // Contact 1 belongs to Acme. Even an invalid patch gets 404: the access check comes first.
        doPatch(GLOBEX_ADMIN, "/api/contacts/1", "{\"company\":\"Hijacked\"}").andExpect(status().isNotFound());
        doPatch(GLOBEX_ADMIN, "/api/contacts/1", "{\"nonsense\":true}").andExpect(status().isNotFound());
        doGet(ACME_ADMIN, "/api/contacts/1").andExpect(jsonPath("$.company", is("Road Runner Ltd")));
    }

    @Test
    void changingOwnerThroughPatchFollowsTheManagerAdminRule() throws Exception {
        // Users: 3 = Rita (rep), 4 = Ravi (rep), 5 = Globex's admin.
        long id = create(ACME_REP_RITA, "/api/contacts", FULL_CONTACT);
        doPatch(ACME_REP_RITA, "/api/contacts/" + id, "{\"ownerId\":4}").andExpect(status().isForbidden());
        doPatch(ACME_MANAGER, "/api/contacts/" + id, "{\"ownerId\":5}").andExpect(status().isBadRequest());
        doPatch(ACME_MANAGER, "/api/contacts/" + id, "{\"ownerId\":4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId", is(4)))
                .andExpect(jsonPath("$.company", is("Fresh Co")));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/api/contacts      | {\"firstName\":\"A\",\"lastName\":\"B\"}",
            "/api/leads         | {\"name\":\"A\"}",
            "/api/opportunities | {\"name\":\"A\"}"
    })
    void patchCannotClearTheOwner(String path, String body) throws Exception {
        long id = create(ACME_REP_RITA, path, body);
        // Even an admin cannot set the owner to null: every record has an owner.
        doPatch(ACME_ADMIN, path + "/" + id, "{\"ownerId\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ownerId", is("must not be null")));
        // Leaving ownerId out keeps the owner (an empty patch changes nothing).
        doPatch(ACME_ADMIN, path + "/" + id, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId", is(3)));
    }

    @Test
    void putWithoutOwnerKeepsTheCurrentOwner() throws Exception {
        // The one exception to "PUT replaces everything": the owner is never cleared.
        long id = create(ACME_REP_RITA, "/api/contacts", FULL_CONTACT);
        doPut(ACME_ADMIN, "/api/contacts/" + id, "{\"firstName\":\"Nina\",\"lastName\":\"New\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId", is(3)))
                .andExpect(jsonPath("$.company", nullValue()));
    }

    // --- Unknown fields are rejected on POST, PUT and PATCH, for every record type ---

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/api/contacts      | \"firstName\":\"A\",\"lastName\":\"B\"",
            "/api/leads         | \"name\":\"A\",\"status\":\"NEW\"",
            "/api/opportunities | \"name\":\"A\",\"stage\":\"PROPOSAL\""
    })
    void unknownFieldsAreRejectedOnPostPutAndPatch(String path, String validFields) throws Exception {
        String valid = "{" + validFields + "}";
        String withOrgId = "{" + validFields + ",\"orgId\":2}";
        String withTypo = "{" + validFields + ",\"stauts\":\"NEW\"}";
        long id = create(ACME_ADMIN, path, valid);

        doPost(ACME_ADMIN, path, withOrgId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.orgId", is("unknown field")));
        doPost(ACME_ADMIN, path, withTypo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stauts", is("unknown field")));

        doPut(ACME_ADMIN, path + "/" + id, withOrgId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.orgId", is("unknown field")));
        doPut(ACME_ADMIN, path + "/" + id, withTypo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stauts", is("unknown field")));

        doPatch(ACME_ADMIN, path + "/" + id, "{\"orgId\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.orgId", is("unknown field")));
        doPatch(ACME_ADMIN, path + "/" + id, "{\"stauts\":\"NEW\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stauts", is("unknown field")));

        // The record is still in Acme (organization 1) and still readable.
        doGet(ACME_ADMIN, path + "/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId", is(1)));
    }

    @Test
    void bodyMustBeAJsonObject() throws Exception {
        doPost(ACME_ADMIN, "/api/contacts", "[1,2,3]").andExpect(status().isBadRequest());
        doPatch(ACME_ADMIN, "/api/contacts/1", "\"just a string\"").andExpect(status().isBadRequest());
    }
}
