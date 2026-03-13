package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class MessagesApiTest extends BaseGatewayTest {

    @Test
    void anthropicCompatibilityWorks() {
        mockProviderAdapter.enqueueCompletionResponse(mockResponse("anthropic", "claude-3-5-sonnet", "Anthropic says hello", 13, 9, 22));

        webTestClient.post().uri("/v1/messages")
                .header("Authorization", "Bearer test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "claude-3-5-sonnet",
                          "messages": [{"role": "user", "content": "anthropic hello"}],
                          "max_tokens": 128
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.type").isEqualTo("message")
                .jsonPath("$.content[0].text").isEqualTo("Anthropic says hello");
    }
}
