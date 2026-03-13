package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

class IamRuleIntegrationTest extends BaseGatewayTest {

    @Test
    void iamRulesCanFilterProviderAndRateLimitRequests() {
        String organizationId = createOrganization(uniqueName("Ops Org"));
        String projectId = createProject(organizationId, uniqueName("Ops Project"));
        EntityExchangeResult<byte[]> keyResult = createApiKey(organizationId, projectId, uniqueName("Ops Key"));
        String apiKeyId = read(keyResult, "/id");
        String rawToken = read(keyResult, "/token");

        webTestClient.post().uri("/keys/api/" + apiKeyId + "/iam")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"rule_type":"PATH","effect":"DENY","pattern":"/v1/images/**"}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/keys/api/" + apiKeyId + "/iam")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"rule_type":"MODEL","effect":"ALLOW","pattern":"gateway-text"}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/keys/api/" + apiKeyId + "/iam")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"rule_type":"RATE","effect":"LIMIT","pattern":"1/60/1"}
                        """)
                .exchange()
                .expectStatus().isOk();

        mockProviderAdapter.enqueueCompletionResponse("anthropic", mockResponse("anthropic", "gateway-text", "IAM anthropic success", 10, 5, 15));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gateway-text","messages":[{"role":"user","content":"provider filtered"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metadata.used_provider").isEqualTo("anthropic");

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gateway-text","messages":[{"role":"user","content":"second request"}]}
                        """)
                .exchange()
                .expectStatus().isEqualTo(429);
    }
}
