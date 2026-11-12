package com.relaycrm;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Role rules inside one organization (Acme). */
class RolePermissionTest extends IntegrationTestBase {

    @Test
    void everyRoleCanCreateAndViewRecordsOfTheirOrganization() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", contactJson("Visible"));
        for (String key : new String[] {ACME_ADMIN, ACME_MANAGER, ACME_REP_RITA, ACME_REP_RAVI}) {
            doGet(key, "/api/contacts/" + id).andExpect(status().isOk());
        }
    }

    @Test
    void repCanEditOwnRecord() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", contactJson("Before"));
        doPut(ACME_REP_RITA, "/api/contacts/" + id, contactJson("After"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName", is("After")));
    }

    @Test
    void repCannotEditRecordOwnedBySomeoneElse() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", contactJson("Ritas"));
        doPut(ACME_REP_RAVI, "/api/contacts/" + id, contactJson("Ravis")).andExpect(status().isForbidden());
        doGet(ACME_REP_RITA, "/api/contacts/" + id).andExpect(jsonPath("$.lastName", is("Ritas")));

        long leadId = create(ACME_REP_RITA, "/api/leads", "{\"name\":\"Ritas lead\"}");
        doPut(ACME_REP_RAVI, "/api/leads/" + leadId, "{\"name\":\"x\"}").andExpect(status().isForbidden());

        long oppId = create(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Ritas deal\"}");
        doPut(ACME_REP_RAVI, "/api/opportunities/" + oppId, "{\"name\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void managerAndAdminCanEditAnyRecordInTheirOrganization() throws Exception {
        long id = create(ACME_REP_RITA, "/api/contacts", contactJson("Ritas"));
        doPut(ACME_MANAGER, "/api/contacts/" + id, contactJson("ByManager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName", is("ByManager")));
        doPut(ACME_ADMIN, "/api/contacts/" + id, contactJson("ByAdmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName", is("ByAdmin")));
    }

    @Test
    void onlyAdminCanDelete() throws Exception {
        for (String path : new String[] {"/api/contacts", "/api/leads", "/api/opportunities"}) {
            String json = path.equals("/api/contacts") ? contactJson("ToDelete") : "{\"name\":\"To delete\"}";
            long id = create(ACME_REP_RITA, path, json);

            doDelete(ACME_REP_RITA, path + "/" + id).andExpect(status().isForbidden());   // even the owner
            doDelete(ACME_MANAGER, path + "/" + id).andExpect(status().isForbidden());
            doGet(ACME_ADMIN, path + "/" + id).andExpect(status().isOk());

            doDelete(ACME_ADMIN, path + "/" + id).andExpect(status().isNoContent());
            doGet(ACME_ADMIN, path + "/" + id).andExpect(status().isNotFound());
        }
    }

    @Test
    void repCannotHandRecordsToOthersButManagerCanReassign() throws Exception {
        // Users: 3 = Rita (rep), 4 = Ravi (rep).
        doPost(ACME_REP_RITA, "/api/contacts", "{\"firstName\":\"A\",\"lastName\":\"B\",\"ownerId\":4}")
                .andExpect(status().isForbidden());

        long id = create(ACME_REP_RITA, "/api/contacts", contactJson("Ritas"));
        doPut(ACME_REP_RITA, "/api/contacts/" + id, "{\"firstName\":\"A\",\"lastName\":\"B\",\"ownerId\":4}")
                .andExpect(status().isForbidden());

        doPut(ACME_MANAGER, "/api/contacts/" + id, "{\"firstName\":\"A\",\"lastName\":\"B\",\"ownerId\":4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId", is(4)));

        // Ownership moved: Ravi can now edit it, Rita no longer can.
        doPut(ACME_REP_RAVI, "/api/contacts/" + id, contactJson("NowRavis")).andExpect(status().isOk());
        doPut(ACME_REP_RITA, "/api/contacts/" + id, contactJson("StillRitas")).andExpect(status().isForbidden());
    }

    @Test
    void invalidBodyIsRejected() throws Exception {
        doPost(ACME_REP_RITA, "/api/contacts", "{\"firstName\":\"\",\"lastName\":\"B\"}")
                .andExpect(status().isBadRequest());
        doPost(ACME_REP_RITA, "/api/opportunities", "{\"name\":\"Deal\",\"amount\":-5}")
                .andExpect(status().isBadRequest());
    }
}
