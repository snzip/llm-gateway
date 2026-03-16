package com.qizlan.llm.gateway;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCompletionsTest extends BaseGatewayTest {

    @Test
    void openAiChatCompletionWorks() {
        mockProviderAdapter.enqueueCompletionResponse("openai", mockResponse("openai", "gpt-4o", "OpenAI says hello", 11, 7, 18));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer test-api-key")
                .header("X-Correlation-Id", "corr-chat-001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "messages": [{"role": "user", "content": "hello gateway"}]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("OpenAI says hello")
                .jsonPath("$.usage.total_tokens").isEqualTo(18);

        var log = requestLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).getFirst();
        org.junit.jupiter.api.Assertions.assertEquals("corr-chat-001", log.getCorrelationId());
        org.junit.jupiter.api.Assertions.assertFalse(log.getTraceId().isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(log.getRequestPayload().contains("\"model\":\"gpt-4o\""));
        org.junit.jupiter.api.Assertions.assertTrue(log.getResponsePayload().contains("OpenAI says hello"));
    }

    @Test
    void googleChatCompletionWorks() {
        mockProviderAdapter.enqueueCompletionResponse("google", mockResponse("google", "gemini-2.0-flash", "Gemini says hello", 8, 6, 14));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gemini-2.0-flash",
                          "messages": [{"role": "user", "content": "hello gemini"}]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("Gemini says hello")
                .jsonPath("$.metadata.used_provider").isEqualTo("google");
    }

    @Test
    void openAiStreamingPassthroughWorks() {
        mockProviderAdapter.enqueueCompletionResponse("openai", mockResponse("openai", "gpt-4o", "stream hello", 11, 7, 18));

        FluxExchangeResult<ServerSentEvent<String>> result = webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("""
                        {
                          "model": "gpt-4o",
                          "stream": true,
                          "messages": [{"role": "user", "content": "hello stream"}]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<>() {
                });

        String body = result.getResponseBody()
                .map(ServerSentEvent::data)
                .collectList()
                .map(parts -> String.join("\n", parts))
                .block(Duration.ofSeconds(5));
        assertTrue(body.contains("stream hello"));
        assertTrue(body.contains("[DONE]"));

        var log = requestLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).getFirst();
        org.junit.jupiter.api.Assertions.assertTrue(log.isStreamed());
        org.junit.jupiter.api.Assertions.assertTrue(log.getTimeToFirstTokenMs() >= 0);
        org.junit.jupiter.api.Assertions.assertFalse(log.getRequestId().isBlank());
    }
}
