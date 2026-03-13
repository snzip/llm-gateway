package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import static org.hamcrest.Matchers.greaterThan;

class ApiKeyManagementIntegrationTest extends BaseGatewayTest {

    @Test
    void controlPlaneCanCreateKeyUseGatewayAndListLogs() {
        String organizationId = createOrganization(uniqueName("Acme"));
        String projectId = createProject(organizationId, uniqueName("Gateway"));
        EntityExchangeResult<byte[]> keyResult = createApiKey(organizationId, projectId, uniqueName("Integration Key"));
        String apiKeyId = read(keyResult, "/id");
        String rawToken = read(keyResult, "/token");

        mockProviderAdapter.enqueueCompletionResponse(mockResponse("openai", "gpt-4o", "Managed key works", 10, 5, 15));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-4o","messages":[{"role":"user","content":"hello with managed key"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("Managed key works");

        EntityExchangeResult<byte[]> logsResult = webTestClient.get().uri("/logs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].path").exists()
                .returnResult();
        String logId = read(logsResult, "/0/id");

        webTestClient.get().uri("/logs/" + logId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estimated_cost_micros_usd").value(greaterThan(0))
                .jsonPath("$.routing_trace[0].provider").exists();

        webTestClient.delete().uri("/keys/api/" + apiKeyId)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-4o","messages":[{"role":"user","content":"hello again"}]}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
