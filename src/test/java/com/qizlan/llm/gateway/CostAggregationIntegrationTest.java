package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import static org.hamcrest.Matchers.greaterThan;

class CostAggregationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void costSummaryAndTimeseriesWorkForProjectTraffic() {
        String organizationId = createOrganization(uniqueName("Cost Org"));
        String projectId = createProject(organizationId, uniqueName("Cost Project"));
        EntityExchangeResult<byte[]> keyResult = createApiKey(organizationId, projectId, uniqueName("Cost Key"));
        String rawToken = read(keyResult, "/token");

        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-cost-1",
                  "choices": [{"message": {"role": "assistant", "content": "cost works"}}],
                  "usage": {"prompt_tokens": 12, "completion_tokens": 6, "total_tokens": 18}
                }
                """));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-4o","messages":[{"role":"user","content":"cost please"}]}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/costs/summary")
                        .queryParam("group_by", "project")
                        .queryParam("project_id", projectId)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].group_value").isEqualTo(projectId)
                .jsonPath("$.data[0].estimated_cost_micros_usd").value(greaterThan(0));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/costs/timeseries")
                        .queryParam("bucket", "day")
                        .queryParam("group_by", "project")
                        .queryParam("project_id", projectId)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].group_value").isEqualTo(projectId);

        webTestClient.post().uri(uriBuilder -> uriBuilder.path("/internal/costs/recompute").queryParam("bucket", "day").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rows").exists();
    }
}
