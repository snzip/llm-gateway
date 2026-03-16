package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.persistence.entity.RequestLogEntity;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CostAggregationIntegrationTest extends BaseControlPlaneIntegrationTest {

    @Test
    void costSummaryAndTimeseriesWorkForProjectTraffic() throws Exception {
        String organizationId = createOrganization(uniqueName("Cost Org"));
        String projectId = createProject(organizationId, uniqueName("Cost Project"));
        String apiKeyId = read(createApiKey(organizationId, projectId, uniqueName("Cost Key")), "/id");

        requestLogRepository.save(new RequestLogEntity(
                "req-cost",
                "/v1/chat/completions",
                "gpt-4o",
                "openai",
                200,
                100,
                12,
                6,
                18,
                1800,
                1200,
                600,
                0,
                0,
                0,
                25,
                false,
                false,
                false,
                false,
                apiKeyId,
                organizationId,
                projectId,
                "[{\"provider\":\"openai\"}]",
                "corr-cost",
                "trace-cost",
                "span-cost",
                "{\"messages\":[]}",
                "{\"choices\":[]}"
        ));

        mockMvc.perform(get("/costs/summary")
                        .param("group_by", "project")
                        .param("project_id", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].group_value").value(projectId))
                .andExpect(jsonPath("$.data[0].estimated_cost_micros_usd").value(greaterThan(0)));

        mockMvc.perform(get("/costs/timeseries")
                        .param("bucket", "day")
                        .param("group_by", "project")
                        .param("project_id", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].group_value").value(projectId));

        mockMvc.perform(post("/internal/costs/recompute").param("bucket", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").exists());
    }
}
