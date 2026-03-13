package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

class AdminControllerIntegrationTest extends BaseGatewayTest {

    @Test
    void adminMetricsEndpointsReturnOrganizationProjectAndCostData() {
        String organizationId = createOrganization(uniqueName("Admin Org"));
        String projectId = createProject(organizationId, uniqueName("Admin Project"));
        EntityExchangeResult<byte[]> keyResult = createApiKey(organizationId, projectId, uniqueName("Admin Key"));
        String rawToken = read(keyResult, "/token");

        mockProviderAdapter.enqueueCompletionResponse("openai", mockResponse("openai", "gpt-4o", "admin works", 9, 4, 13));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-4o","messages":[{"role":"user","content":"admin metrics"}]}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/admin/metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.request_count").exists();

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/admin/metrics/timeseries").queryParam("bucket", "day").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray();

        webTestClient.get().uri("/admin/metrics/cost-by-model")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray();

        webTestClient.get().uri("/admin/organizations")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[?(@.id=='" + organizationId + "')]").exists();

        webTestClient.get().uri("/admin/organizations/" + organizationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(organizationId);

        webTestClient.get().uri("/admin/organizations/" + organizationId + "/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].id").isEqualTo(projectId);

        webTestClient.get().uri("/admin/organizations/" + organizationId + "/projects/" + projectId + "/metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.request_count").exists();

        webTestClient.get().uri("/admin/organizations/" + organizationId + "/projects/" + projectId + "/logs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].path").isEqualTo("/v1/chat/completions")
                .jsonPath("$.data[0].provider_id").isEqualTo("openai");
    }
}
