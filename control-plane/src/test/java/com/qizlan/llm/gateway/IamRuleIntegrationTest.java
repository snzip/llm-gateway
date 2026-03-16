package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamRuleIntegrationTest extends BaseControlPlaneIntegrationTest {

    @Test
    void iamRulesCanBeCreatedListedUpdatedAndDeleted() throws Exception {
        String organizationId = createOrganization(uniqueName("Ops Org"));
        String projectId = createProject(organizationId, uniqueName("Ops Project"));
        String apiKeyId = read(createApiKey(organizationId, projectId, uniqueName("Ops Key")), "/id");

        mockMvc.perform(post("/keys/api/{id}/iam", apiKeyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rule_type":"PATH","effect":"DENY","pattern":"/v1/images/**"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/keys/api/{id}/iam", apiKeyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rule_type":"MODEL","effect":"ALLOW","pattern":"gateway-text"}
                                """))
                .andExpect(status().isOk());

        var rateRule = mockMvc.perform(post("/keys/api/{id}/iam", apiKeyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rule_type":"RATE","effect":"LIMIT","pattern":"1/60/1"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String ruleId = read(rateRule, "/id");

        mockMvc.perform(get("/keys/api/{id}/iam", apiKeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + ruleId + "')]").exists());

        mockMvc.perform(patch("/keys/api/{id}/iam/{ruleId}", apiKeyId, ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pattern":"2/60/1","active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pattern").value("2/60/1"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/keys/api/{id}/iam/{ruleId}", apiKeyId, ruleId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/keys/api/{id}/iam", apiKeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + ruleId + "')]").doesNotExist());
    }
}
