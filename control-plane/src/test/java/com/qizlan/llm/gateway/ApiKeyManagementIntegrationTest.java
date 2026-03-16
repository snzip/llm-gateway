package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.persistence.entity.RequestLogEntity;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiKeyManagementIntegrationTest extends BaseControlPlaneIntegrationTest {

    @Test
    void controlPlaneCanCreateListInspectAndRevokeApiKeys() throws Exception {
        String organizationId = createOrganization(uniqueName("Acme"));
        String projectId = createProject(organizationId, uniqueName("Gateway"));
        var keyResult = createApiKey(organizationId, projectId, uniqueName("Integration Key"));
        String apiKeyId = read(keyResult, "/id");

        RequestLogEntity log = requestLogRepository.save(new RequestLogEntity(
                "req-key",
                "/v1/chat/completions",
                "gpt-4o",
                "openai",
                200,
                80,
                10,
                5,
                15,
                1500,
                1000,
                500,
                0,
                0,
                0,
                20,
                false,
                false,
                false,
                false,
                apiKeyId,
                organizationId,
                projectId,
                "[{\"provider\":\"openai\"}]",
                "corr-key",
                "trace-key",
                "span-key",
                "{\"messages\":[]}",
                "{\"choices\":[]}"
        ));

        mockMvc.perform(get("/keys/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + apiKeyId + "')]").exists());

        mockMvc.perform(get("/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].path").value("/v1/chat/completions"));

        mockMvc.perform(get("/logs/{id}", log.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimated_cost_micros_usd").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.routing_trace[0].provider").value("openai"));

        mockMvc.perform(delete("/keys/api/{id}", apiKeyId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/keys/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + apiKeyId + "')].active").value(false));
    }
}
