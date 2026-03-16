package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.persistence.entity.RequestLogEntity;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerIntegrationTest extends BaseControlPlaneIntegrationTest {

    @Test
    void adminMetricsEndpointsReturnOrganizationProjectAndCostData() throws Exception {
        String organizationId = createOrganization(uniqueName("Admin Org"));
        String projectId = createProject(organizationId, uniqueName("Admin Project"));
        String apiKeyId = read(createApiKey(organizationId, projectId, uniqueName("Admin Key")), "/id");
        String adminToken = loginAsAdmin();

        requestLogRepository.save(new RequestLogEntity(
                "req-admin",
                "/v1/chat/completions",
                "gpt-4o",
                "openai",
                200,
                120,
                9,
                4,
                13,
                1300,
                900,
                400,
                0,
                0,
                0,
                30,
                false,
                false,
                false,
                false,
                apiKeyId,
                organizationId,
                projectId,
                "[{\"provider\":\"openai\"}]",
                "corr-admin",
                "trace-admin",
                "span-admin",
                "{\"messages\":[]}",
                "{\"choices\":[]}"
        ));

        mockMvc.perform(get("/admin/metrics").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request_count").value(greaterThan(0)));

        mockMvc.perform(get("/admin/metrics/timeseries").header("Authorization", "Bearer " + adminToken).param("bucket", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/admin/metrics/cost-by-model").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.group_value=='gpt-4o')]").exists());

        mockMvc.perform(get("/admin/organizations").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + organizationId + "')]").exists());

        mockMvc.perform(get("/admin/organizations/{orgId}", organizationId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(organizationId));

        mockMvc.perform(get("/admin/organizations/{orgId}/projects", organizationId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(projectId));

        mockMvc.perform(get("/admin/organizations/{orgId}/projects/{projectId}/metrics", organizationId, projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request_count").value(1));

        mockMvc.perform(get("/admin/organizations/{orgId}/projects/{projectId}/logs", organizationId, projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].path").value("/v1/chat/completions"))
                .andExpect(jsonPath("$.data[0].provider_id").value("openai"));
    }
}
