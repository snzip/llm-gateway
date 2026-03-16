package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogIntegrationTest extends BaseControlPlaneIntegrationTest {

    @Test
    void auditLogCarriesExplicitRequestContextHeaders() throws Exception {
        var result = mockMvc.perform(post("/orgs")
                        .header("X-Correlation-Id", "corr-123")
                        .header("X-Actor-Type", "user")
                        .header("X-Actor-Id", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Context Org\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-123"))
                .andReturn();
        String organizationId = read(result, "/id");

        mockMvc.perform(get("/audit-logs/{organizationId}", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].correlation_id").value("corr-123"))
                .andExpect(jsonPath("$.data[0].actor_type").value("user"))
                .andExpect(jsonPath("$.data[0].actor_id").value("alice"))
                .andExpect(jsonPath("$.data[0].action").value("organization.create"));

        mockMvc.perform(get("/audit-logs/{organizationId}/filters", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actions").isArray());
    }
}
