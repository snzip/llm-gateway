package com.qizlan.llm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.core.ParameterizedTypeReference;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayApplicationTests {

    private static final BlockingQueue<MockResponse> OPENAI_RESPONSES = new LinkedBlockingQueue<>();
    private static final BlockingQueue<MockResponse> ANTHROPIC_RESPONSES = new LinkedBlockingQueue<>();
    private static final BlockingQueue<MockResponse> GOOGLE_RESPONSES = new LinkedBlockingQueue<>();
    private static final MockWebServer OPENAI = startServer(OPENAI_RESPONSES);
    private static final MockWebServer ANTHROPIC = startServer(ANTHROPIC_RESPONSES);
    private static final MockWebServer GOOGLE = startServer(GOOGLE_RESPONSES);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("llm.gateway.providers.mode", () -> "real");
        registry.add("llm.gateway.providers.openai.enabled", () -> true);
        registry.add("llm.gateway.providers.openai.base-url", () -> OPENAI.url("/").toString());
        registry.add("llm.gateway.providers.openai.api-key", () -> "openai-test-key");
        registry.add("llm.gateway.providers.anthropic.enabled", () -> true);
        registry.add("llm.gateway.providers.anthropic.base-url", () -> ANTHROPIC.url("/").toString());
        registry.add("llm.gateway.providers.anthropic.api-key", () -> "anthropic-test-key");
        registry.add("llm.gateway.providers.google.enabled", () -> true);
        registry.add("llm.gateway.providers.google.base-url", () -> GOOGLE.url("/").toString());
        registry.add("llm.gateway.providers.google.api-key", () -> "google-test-key");
    }

    @AfterAll
    static void shutdownServers() throws IOException {
        OPENAI.shutdown();
        ANTHROPIC.shutdown();
        GOOGLE.shutdown();
    }

    @BeforeEach
    void clearMockResponses() {
        OPENAI_RESPONSES.clear();
        ANTHROPIC_RESPONSES.clear();
        GOOGLE_RESPONSES.clear();
    }

    @Test
    void healthEndpointWorks() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.health.status").isEqualTo("UP");
    }

    @Test
    void modelsEndpointWorksWithoutAuth() {
        webTestClient.get().uri("/v1/models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[?(@.id=='gpt-4o')]").exists()
                .jsonPath("$.data[?(@.id=='claude-3-5-sonnet')]").exists()
                .jsonPath("$.data[?(@.id=='gemini-2.0-flash')]").exists()
                .jsonPath("$.data[?(@.id=='gateway-text')]").exists();
    }

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
    void anthropicCompatibilityWorks() {
        ANTHROPIC_RESPONSES.add(json("""
                {
                  "id": "msg_123",
                  "type": "message",
                  "content": [{"type": "text", "text": "Anthropic says hello"}],
                  "usage": {"input_tokens": 13, "output_tokens": 9}
                }
                """));

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
    }

    @Test
    void imageEndpointsAndResponsesPlaceholderWork() {
        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"inlineData": {"data": "R0lGODlhAQABAIAAAAUEBA=="}}]
                    }
                  }]
                }
                """));

        webTestClient.post().uri("/v1/images/generations")
                .header("Authorization", "Bearer test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"prompt":"draw a cat","model":"gemini-2.5-flash-image"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].b64_json").isEqualTo("R0lGODlhAQABAIAAAAUEBA==");

        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"inlineData": {"data": "Q0FUVEVTVA=="}}]
                    }
                  }]
                }
                """));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("prompt", "edit this");
        builder.part("image", new NamedByteArrayResource("test.png", "png".getBytes()))
                .contentType(MediaType.IMAGE_PNG);

        webTestClient.post().uri("/v1/images/edits")
                .header("Authorization", "Bearer test-api-key")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].revised_prompt").isEqualTo("edit this");

        webTestClient.post().uri("/v1/responses")
                .header("Authorization", "Bearer test-api-key")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class).value(body -> assertTrue(body.contains("not supported")));

        webTestClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-4o","messages":[{"role":"user","content":"hello gateway"}]}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void controlPlaneCanCreateKeyUseGatewayAndListLogs() {
        String organizationId = createOrganization("Acme");
        String projectId = createProject(organizationId, "Gateway");
        EntityExchangeResult<byte[]> keyResult = createApiKey(organizationId, projectId, "Integration Key");
        String apiKeyId = read(keyResult, "/id");
        String rawToken = read(keyResult, "/token");

        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-2",
                  "choices": [{"message": {"role": "assistant", "content": "Managed key works"}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(MediaType.APPLICATION_JSON)
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
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-4o","messages":[{"role":"user","content":"hello again"}]}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void auditLogCarriesExplicitRequestContextHeaders() {
        EntityExchangeResult<byte[]> result = webTestClient.post().uri("/orgs")
                .header("X-Correlation-Id", "corr-123")
                .header("X-Actor-Type", "user")
                .header("X-Actor-Id", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Context Org"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "corr-123")
                .expectBody()
                .returnResult();
        String organizationId = read(result, "/id");

        webTestClient.get().uri("/audit-logs/" + organizationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].correlation_id").isEqualTo("corr-123")
                .jsonPath("$.data[0].actor_type").isEqualTo("user")
                .jsonPath("$.data[0].actor_id").isEqualTo("alice")
                .jsonPath("$.data[0].action").isEqualTo("organization.create");
    }

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
                .contentType(MediaType.APPLICATION_JSON)
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
                .contentType(MediaType.APPLICATION_JSON)
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
    void iamRateCostAuditAndModelSyncApisWork() {
        String organizationId = createOrganization("Ops Org");
        String projectId = createProject(organizationId, "Ops Project");
        EntityExchangeResult<byte[]> keyResult = createApiKey(organizationId, projectId, "Ops Key");
        String apiKeyId = read(keyResult, "/id");
        String rawToken = read(keyResult, "/token");

        webTestClient.post().uri("/keys/api/" + apiKeyId + "/iam")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"rule_type":"PATH","effect":"DENY","pattern":"/v1/images/**"}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/keys/api/" + apiKeyId + "/iam")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"rule_type":"MODEL","effect":"ALLOW","pattern":"gateway-text"}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/keys/api/" + apiKeyId + "/iam")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"rule_type":"RATE","effect":"LIMIT","pattern":"1/60/1"}
                        """)
                .exchange()
                .expectStatus().isOk();

        ANTHROPIC_RESPONSES.add(json("""
                {
                  "id": "msg_iam_1",
                  "type": "message",
                  "content": [{"type": "text", "text": "IAM anthropic success"}],
                  "usage": {"input_tokens": 10, "output_tokens": 5}
                }
                """));

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gateway-text","messages":[{"role":"user","content":"provider filtered"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metadata.used_provider").isEqualTo("anthropic");

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gateway-text","messages":[{"role":"user","content":"second request"}]}
                        """)
                .exchange()
                .expectStatus().isEqualTo(429);

        webTestClient.get().uri("/audit-logs/" + organizationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].action").exists();

        OPENAI_RESPONSES.add(json("""
                {
                  "data": [
                    {"id": "gpt-4.1-mini"},
                    {"id": "gpt-image-1"}
                  ]
                }
                """));

        webTestClient.post().uri(uriBuilder -> uriBuilder.path("/internal/models/sync").queryParam("provider", "openai").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.synced_mappings").value(greaterThanOrEqualTo(1));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/internal/models/sync/history").queryParam("provider", "openai").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].detail").value(containsString("OVERRIDE_MANUAL"));
    }

    @Test
    void guardrailsAdminMetricsProviderProbeAndMcpWork() {
        String organizationId = createOrganization("Guardrail Org");
        String projectId = createProject(organizationId, "Guardrail Project");
        String rawToken = read(createApiKey(organizationId, projectId, "Guardrail Key"), "/token");

        webTestClient.post().uri("/guardrails/rules/" + organizationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Block badword","rule_type":"KEYWORD","pattern":"badword","action":"BLOCK"}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-4o","messages":[{"role":"user","content":"contains badword"}]}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.get().uri("/admin/metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.request_count").exists();

        OPENAI_RESPONSES.add(json("""
                {
                  "data": [{"id":"gpt-4o"}]
                }
                """));
        OPENAI_RESPONSES.add(json("""
                {
                  "id":"chatcmpl-probe",
                  "choices":[{"message":{"role":"assistant","content":"pong"}}],
                  "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}
                }
                """));

        webTestClient.post().uri(uriBuilder -> uriBuilder.path("/internal/providers/probe").queryParam("provider", "openai").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.provider_id").isEqualTo("openai")
                .jsonPath("$.data.healthy").isEqualTo(true);

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/internal/providers/probe/history").queryParam("provider", "openai").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].provider_id").isEqualTo("openai");

        EntityExchangeResult<byte[]> registerResult = webTestClient.post().uri("/oauth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"client_name":"Gateway MCP Client","redirect_uri":"https://client.example/callback"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        String clientId = read(registerResult, "/client_id");

        EntityExchangeResult<byte[]> authorizeResult = webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/oauth/authorize")
                        .queryParam("client_id", clientId)
                        .queryParam("redirect_uri", "https://client.example/callback")
                        .queryParam("state", "abc")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        String code = read(authorizeResult, "/code");

        EntityExchangeResult<byte[]> tokenResult = webTestClient.post().uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"grant_type":"authorization_code","code":"%s","scope":"mcp tools"}
                        """.formatted(code))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        String accessToken = read(tokenResult, "/access_token");

        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-mcp",
                  "choices": [{"message": {"role": "assistant", "content": "MCP says hello"}}],
                  "usage": {"prompt_tokens": 9, "completion_tokens": 4, "total_tokens": 13}
                }
                """));

        webTestClient.post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"method":"tools/call","name":"chat","arguments":{"model":"gpt-4o","prompt":"hello from mcp"}}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post().uri("/mcp")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"method":"tools/call","name":"chat","arguments":{"model":"gpt-4o","prompt":"hello from mcp"}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result.choices[0].message.content").isEqualTo("MCP says hello");
    }

    private String createOrganization(String name) {
        EntityExchangeResult<byte[]> result = webTestClient.post().uri("/orgs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"%s\"}".formatted(name))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        return read(result, "/id");
    }

    private String createProject(String organizationId, String name) {
        EntityExchangeResult<byte[]> result = webTestClient.post().uri("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"organizationId":"%s","name":"%s"}
                        """.formatted(organizationId, name))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        return read(result, "/id");
    }

    private EntityExchangeResult<byte[]> createApiKey(String organizationId, String projectId, String name) {
        return webTestClient.post().uri("/keys/api")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"organizationId":"%s","projectId":"%s","name":"%s"}
                        """.formatted(organizationId, projectId, name))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
    }

    private String read(EntityExchangeResult<byte[]> result, String pointer) {
        try {
            JsonNode root = objectMapper.readTree(result.getResponseBodyContent());
            JsonNode node = root.at(pointer);
            return node.isMissingNode() || node.isNull() ? "" : node.asText();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static MockWebServer startServer(BlockingQueue<MockResponse> queue) {
        try {
            MockWebServer server = new MockWebServer();
            server.setDispatcher(queueDispatcher(queue));
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Dispatcher queueDispatcher(BlockingQueue<MockResponse> queue) {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                MockResponse response = queue.poll();
                if (response != null) {
                    return response;
                }
                return new MockResponse()
                        .setResponseCode(500)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"error\":{\"message\":\"no mock response queued\"}}");
            }
        };
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(String filename, byte[] byteArray) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
