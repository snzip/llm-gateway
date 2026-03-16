package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuardrailIntegrationTest extends BaseControlPlaneIntegrationTest {

    @Test
    void guardrailEndpointsCanCreateBlockAndReportViolations() throws Exception {
        String organizationId = createOrganization(uniqueName("Guardrail Org"));

        mockMvc.perform(post("/guardrails/rules/{organizationId}", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Block badword","rule_type":"KEYWORD","pattern":"badword","action":"BLOCK"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/guardrails/rules/{organizationId}", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Block badword"));

        mockMvc.perform(post("/guardrails/test/{organizationId}", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"contains badword"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));

        mockMvc.perform(get("/guardrails/violations/{organizationId}", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].organization_id").value(organizationId));

        mockMvc.perform(get("/guardrails/stats/{organizationId}", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violation_count").value(1));

        mockMvc.perform(get("/guardrails/system-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rule_type").value("KEYWORD"));
    }
}
