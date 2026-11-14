package com.relaycrm;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The OpenAPI description the frontend generates its types from. The
 * controllers read bodies as raw JSON, so without annotations every request
 * body would be described as "any object". These checks keep it typed.
 */
class ApiDocsTest extends IntegrationTestBase {

    private static final String REF = "#/components/schemas/";

    @ParameterizedTest
    @CsvSource({
            "/api/contacts,      ContactRequest",
            "/api/leads,         LeadRequest",
            "/api/opportunities, OpportunityRequest",
            "/api/tasks,         TaskRequest"
    })
    void everyRequestBodyIsDescribedByItsRealSchema(String path, String schema) throws Exception {
        String item = "$.paths['" + path + "/{id}']";
        // Public, like the source code would be: no API key needed.
        var docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        docs.andExpect(jsonPath("$.paths['" + path + "'].post.requestBody.content['application/json'].schema.$ref",
                        is(REF + schema)))
                .andExpect(jsonPath(item + ".patch.requestBody.content['application/merge-patch+json'].schema.$ref",
                        is(REF + schema)))
                .andExpect(jsonPath("$.components.schemas." + schema + ".properties.ownerId.type", is("integer")));
        if (!path.equals("/api/tasks")) {
            docs.andExpect(jsonPath(item + ".put.requestBody.content['application/json'].schema.$ref", is(REF + schema)));
        }
    }

    @ParameterizedTest
    @CsvSource({"OpportunityRequest, stage, WON", "LeadRequest, status, QUALIFIED", "TaskRequest, status, DONE"})
    void enumsAreListedSoTheFrontendKnowsTheValues(String schema, String field, String value) throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas." + schema + ".properties." + field + ".enum", hasItem(value)));
    }
}
