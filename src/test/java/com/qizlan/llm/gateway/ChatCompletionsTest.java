package com.qizlan.llm.gateway;

import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCompletionsTest extends AbstractIntegrationTest {

    @Test
    void openAiChatCompletionWorks() {
        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-openai",
                  "choices": [{"message": {"role": "assistant", "content": "OpenAI says hello"}}],
                  "usage": {"prompt_tokens": 11, "completion_tokens": 7, "total_tokens": 18}
                }
                """));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer test-api-key")
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
    }

    @Test
    void googleChatCompletionWorks() {
        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {"parts": [{"text": "Gemini says hello"}]}
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 8,
                    "candidatesTokenCount": 6,
                    "totalTokenCount": 14
                  }
                }
                """));

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
        OPENAI_RESPONSES.add(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("""
                        data: {"id":"chatcmpl_stream","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"role":"assistant","content":"stream hello"},"finish_reason":null}]}

                        data: [DONE]

                        """));

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

        webTestClient.get().uri("/logs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].streamed").isEqualTo(true)
                .jsonPath("$[0].time_to_first_token_ms").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0))
                .jsonPath("$[0].request_id").exists();
    }
}
