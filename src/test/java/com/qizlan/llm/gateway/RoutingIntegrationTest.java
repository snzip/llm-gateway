package com.qizlan.llm.gateway;

import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void routingFallbackAndProviderHealthWork() {
        int openAiBefore = OPENAI.getRequestCount();
        int anthropicBefore = ANTHROPIC.getRequestCount();

        OPENAI_RESPONSES.add(new MockResponse().setResponseCode(500).setHeader("Content-Type", "application/json").setBody("""
                {"error":{"message":"openai down"}}
                """));
        ANTHROPIC_RESPONSES.add(json("""
                {
                  "id": "msg_fallback_1",
                  "type": "message",
                  "content": [{"type": "text", "text": "Anthropic fallback response"}],
                  "usage": {"input_tokens": 9, "output_tokens": 4}
                }
                """));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer test-api-key")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gateway-text","messages":[{"role":"user","content":"fallback please"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("Anthropic fallback response")
                .jsonPath("$.metadata.used_provider").isEqualTo("anthropic")
                .jsonPath("$.metadata.routing[0].provider").isEqualTo("openai")
                .jsonPath("$.metadata.routing[0].succeeded").isEqualTo(false)
                .jsonPath("$.metadata.routing[1].provider").isEqualTo("anthropic")
                .jsonPath("$.metadata.routing[1].succeeded").isEqualTo(true);

        ANTHROPIC_RESPONSES.add(json("""
                {
                  "id": "msg_fallback_2",
                  "type": "message",
                  "content": [{"type": "text", "text": "Anthropic still primary while openai cools down"}],
                  "usage": {"input_tokens": 7, "output_tokens": 3}
                }
                """));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer test-api-key")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gateway-text","messages":[{"role":"user","content":"second attempt"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("Anthropic still primary while openai cools down")
                .jsonPath("$.metadata.routing[0].provider").isEqualTo("anthropic")
                .jsonPath("$.metadata.routing.length()").isEqualTo(1);

        webTestClient.get().uri("/internal/providers/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.openai.consecutiveFailures").value(greaterThanOrEqualTo(1))
                .jsonPath("$.data.openai.healthy").isEqualTo(false);

        assertEquals(openAiBefore + 1, OPENAI.getRequestCount());
        assertEquals(anthropicBefore + 2, ANTHROPIC.getRequestCount());
    }

    @Test
    void upstreamClientErrorDoesNotFallbackAndReturnsSameStatus() {
        int anthropicBefore = ANTHROPIC.getRequestCount();

        OPENAI_RESPONSES.add(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"error":{"message":"bad request"}}
                        """));
        ANTHROPIC_RESPONSES.add(json("""
                {
                  "id": "msg_should_not_run",
                  "type": "message",
                  "content": [{"type": "text", "text": "should not fallback"}],
                  "usage": {"input_tokens": 1, "output_tokens": 1}
                }
                """));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer test-api-key")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gateway-text","messages":[{"role":"user","content":"bad input"}]}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(containsString("openai upstream error: 400"));

        assertEquals(anthropicBefore, ANTHROPIC.getRequestCount());
    }

    @Test
    void upstreamRateLimitFallsBackToNextProvider() {
        OPENAI_RESPONSES.add(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"error":{"message":"rate limited"}}
                        """));
        ANTHROPIC_RESPONSES.add(json("""
                {
                  "id": "msg_rate_limit",
                  "type": "message",
                  "content": [{"type": "text", "text": "Anthropic after 429"}],
                  "usage": {"input_tokens": 9, "output_tokens": 4}
                }
                """));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer test-api-key")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gateway-text","messages":[{"role":"user","content":"retry after 429"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("Anthropic after 429")
                .jsonPath("$.metadata.routing[0].error_type").isEqualTo("rate_limited")
                .jsonPath("$.metadata.routing[1].provider").isEqualTo("anthropic");
    }
}
