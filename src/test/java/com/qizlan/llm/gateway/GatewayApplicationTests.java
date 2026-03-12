package com.qizlan.llm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayApplicationTests {

    private static final BlockingQueue<MockResponse> OPENAI_RESPONSES = new LinkedBlockingQueue<>();
    private static final BlockingQueue<MockResponse> ANTHROPIC_RESPONSES = new LinkedBlockingQueue<>();
    private static final BlockingQueue<MockResponse> GOOGLE_RESPONSES = new LinkedBlockingQueue<>();
    private static final MockWebServer OPENAI = startServer(OPENAI_RESPONSES);
    private static final MockWebServer ANTHROPIC = startServer(ANTHROPIC_RESPONSES);
    private static final MockWebServer GOOGLE = startServer(GOOGLE_RESPONSES);

    @Autowired
    private MockMvc mockMvc;

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
    void healthEndpointWorks() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.health.status").value("UP"));
    }

    @Test
    void modelsEndpointWorksWithoutAuth() throws Exception {
        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='gpt-4o')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='claude-3-5-sonnet')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='gemini-2.0-flash')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='gateway-text')]").exists());
    }

    @Test
    void openAiChatCompletionWorks() throws Exception {
        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-openai",
                  "choices": [{"message": {"role": "assistant", "content": "OpenAI says hello"}}],
                  "usage": {"prompt_tokens": 11, "completion_tokens": 7, "total_tokens": 18}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "hello gateway"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value("OpenAI says hello"))
                .andExpect(jsonPath("$.usage.total_tokens").value(18));
    }

    @Test
    void anthropicCompatibilityWorks() throws Exception {
        ANTHROPIC_RESPONSES.add(json("""
                {
                  "id": "msg_123",
                  "type": "message",
                  "content": [{"type": "text", "text": "Anthropic says hello"}],
                  "usage": {"input_tokens": 13, "output_tokens": 9}
                }
                """));

        mockMvc.perform(post("/v1/messages")
                        .header("Authorization", "Bearer test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "claude-3-5-sonnet",
                                  "messages": [
                                    {"role": "user", "content": "anthropic hello"}
                                  ],
                                  "max_tokens": 128
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("message"))
                .andExpect(jsonPath("$.content[0].text").value("Anthropic says hello"));
    }

    @Test
    void googleChatCompletionWorks() throws Exception {
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

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gemini-2.0-flash",
                                  "messages": [
                                    {"role": "user", "content": "hello gemini"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value("Gemini says hello"))
                .andExpect(jsonPath("$.metadata.used_provider").value("google"));
    }

    @Test
    void openAiStreamingPassthroughWorks() throws Exception {
        OPENAI_RESPONSES.add(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("""
                        data: {"id":"chatcmpl_stream","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"role":"assistant","content":"stream hello"},"finish_reason":null}]}

                        data: [DONE]

                        """));

        MvcResult result = mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "stream": true,
                                  "messages": [
                                    {"role": "user", "content": "hello stream"}
                                  ]
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stream hello")))
                .andExpect(content().string(containsString("[DONE]")));
    }

    @Test
    void imageGenerationWorks() throws Exception {
        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"inlineData": {"data": "R0lGODlhAQABAIAAAAUEBA=="}}]
                    }
                  }]
                }
                """));

        mockMvc.perform(post("/v1/images/generations")
                        .header("Authorization", "Bearer test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "draw a cat",
                                  "model": "gemini-2.5-flash-image"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].b64_json").value("R0lGODlhAQABAIAAAAUEBA=="));
    }

    @Test
    void imageEditMultipartWorks() throws Exception {
        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"inlineData": {"data": "Q0FUVEVTVA=="}}]
                    }
                  }]
                }
                """));

        MockMultipartFile image = new MockMultipartFile("image", "test.png", "image/png", "png".getBytes());
        MockMultipartFile prompt = new MockMultipartFile("prompt", "", "text/plain", "edit this".getBytes());

        mockMvc.perform(multipart("/v1/images/edits")
                        .file(image)
                        .file(prompt)
                        .header("Authorization", "Bearer test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].revised_prompt").value("edit this"));
    }

    @Test
    void responsesApiPlaceholderWorks() throws Exception {
        mockMvc.perform(post("/v1/responses")
                        .header("Authorization", "Bearer test-api-key"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("not supported")));
    }

    @Test
    void unauthorizedRequestRejected() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "hello gateway"}
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void controlPlaneCanCreateKeyUseGatewayAndListLogs() throws Exception {
        MvcResult orgResult = mockMvc.perform(post("/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String organizationId = read(orgResult, "/id");

        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "name":"Gateway"
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = read(projectResult, "/id");

        MvcResult keyResult = mockMvc.perform(post("/keys/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "projectId":"%s",
                                  "name":"Integration Key"
                                }
                                """.formatted(organizationId, projectId)))
                .andExpect(status().isOk())
                .andReturn();
        String apiKeyId = read(keyResult, "/id");
        String rawToken = read(keyResult, "/token");

        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-2",
                  "choices": [{"message": {"role": "assistant", "content": "Managed key works"}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "hello with managed key"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value("Managed key works"));

        MvcResult logsResult = mockMvc.perform(get("/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.path=='/v1/chat/completions')]").exists())
                .andReturn();
        String logId = read(logsResult, "/0/id");

        mockMvc.perform(get("/logs/" + logId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimated_cost_micros_usd").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.routing_trace[0].provider").exists());

        mockMvc.perform(delete("/keys/api/" + apiKeyId))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "hello again"}
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void routingFallbackAndProviderHealthWork() throws Exception {
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

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gateway-text",
                                  "messages": [
                                    {"role": "user", "content": "fallback please"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value("Anthropic fallback response"))
                .andExpect(jsonPath("$.metadata.used_provider").value("anthropic"))
                .andExpect(jsonPath("$.metadata.routing[0].provider").value("openai"))
                .andExpect(jsonPath("$.metadata.routing[0].succeeded").value(false))
                .andExpect(jsonPath("$.metadata.routing[1].provider").value("anthropic"))
                .andExpect(jsonPath("$.metadata.routing[1].succeeded").value(true));

        ANTHROPIC_RESPONSES.add(json("""
                {
                  "id": "msg_fallback_2",
                  "type": "message",
                  "content": [{"type": "text", "text": "Anthropic still primary while openai cools down"}],
                  "usage": {"input_tokens": 7, "output_tokens": 3}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gateway-text",
                                  "messages": [
                                    {"role": "user", "content": "second attempt"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value("Anthropic still primary while openai cools down"))
                .andExpect(jsonPath("$.metadata.routing[0].provider").value("anthropic"))
                .andExpect(jsonPath("$.metadata.routing.length()").value(1));

        mockMvc.perform(get("/internal/providers/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openai.consecutiveFailures").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.openai.healthy").value(false));

        mockMvc.perform(get("/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].routing_trace[0].provider").exists());

        org.junit.jupiter.api.Assertions.assertEquals(openAiBefore + 1, OPENAI.getRequestCount());
        org.junit.jupiter.api.Assertions.assertEquals(anthropicBefore + 2, ANTHROPIC.getRequestCount());
    }

    @Test
    void iamRulesRestrictModelProviderPathAndBudget() throws Exception {
        MvcResult orgResult = mockMvc.perform(post("/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"IAM Org"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String organizationId = read(orgResult, "/id");

        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "name":"IAM Project"
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = read(projectResult, "/id");

        MvcResult keyResult = mockMvc.perform(post("/keys/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "projectId":"%s",
                                  "name":"IAM Key"
                                }
                                """.formatted(organizationId, projectId)))
                .andExpect(status().isOk())
                .andReturn();
        String apiKeyId = read(keyResult, "/id");
        String rawToken = read(keyResult, "/token");

        MvcResult pathRuleResult = mockMvc.perform(post("/keys/api/" + apiKeyId + "/iam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rule_type":"PATH",
                                  "effect":"DENY",
                                  "pattern":"/v1/images/**"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rule_type").value("PATH"))
                .andReturn();
        String pathRuleId = read(pathRuleResult, "/id");

        mockMvc.perform(post("/keys/api/" + apiKeyId + "/iam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rule_type":"MODEL",
                                  "effect":"ALLOW",
                                  "pattern":"gateway-text"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/keys/api/" + apiKeyId + "/iam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rule_type":"PROVIDER",
                                  "effect":"DENY",
                                  "pattern":"openai"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/keys/api/" + apiKeyId + "/iam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "should fail model policy"}
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());

        ANTHROPIC_RESPONSES.add(json("""
                {
                  "id": "msg_iam_1",
                  "type": "message",
                  "content": [{"type": "text", "text": "IAM anthropic success"}],
                  "usage": {"input_tokens": 10, "output_tokens": 5}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gateway-text",
                                  "messages": [
                                    {"role": "user", "content": "provider filtered"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.used_provider").value("anthropic"))
                .andExpect(jsonPath("$.metadata.routing[0].provider").value("anthropic"));

        mockMvc.perform(post("/v1/images/generations")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "blocked image",
                                  "model": "gemini-2.5-flash-image"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/keys/api/" + apiKeyId + "/iam/" + pathRuleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"inlineData": {"data": "SUFNQUdF"}}]
                    }
                  }]
                }
                """));

        mockMvc.perform(post("/v1/images/generations")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "allowed after path rule disabled",
                                  "model": "gemini-2.5-flash-image"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/keys/api/" + apiKeyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "budget_micros_usd": 100
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget_micros_usd").value(100));

        ANTHROPIC_RESPONSES.add(json("""
                {
                  "id": "msg_iam_2",
                  "type": "message",
                  "content": [{"type": "text", "text": "Consumes budget"}],
                  "usage": {"input_tokens": 10, "output_tokens": 5}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gateway-text",
                                  "messages": [
                                    {"role": "user", "content": "consume budget"}
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void costAggregationApisWork() throws Exception {
        MvcResult orgResult = mockMvc.perform(post("/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cost Org"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String organizationId = read(orgResult, "/id");

        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "name":"Cost Project"
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = read(projectResult, "/id");

        MvcResult keyResult = mockMvc.perform(post("/keys/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "projectId":"%s",
                                  "name":"Cost Key"
                                }
                                """.formatted(organizationId, projectId)))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken = read(keyResult, "/token");

        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-cost-openai",
                  "choices": [{"message": {"role": "assistant", "content": "Cost openai"}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """));
        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {"parts": [{"text": "Cost gemini"}]}
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 8,
                    "candidatesTokenCount": 4,
                    "totalTokenCount": 12
                  }
                }
                """));
        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"inlineData": {"data": "Q09TVElNQUdF"}}]
                    }
                  }]
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "cost openai"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gemini-2.0-flash",
                                  "messages": [
                                    {"role": "user", "content": "cost gemini"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/images/generations")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "cost image",
                                  "model": "gemini-2.5-flash-image"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/costs/recompute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hour_rows").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.day_rows").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/costs/summary")
                        .param("group_by", "provider")
                        .param("organization_id", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.group_value=='openai')]").exists())
                .andExpect(jsonPath("$.data[?(@.group_value=='google')]").exists());

        mockMvc.perform(get("/costs/summary")
                        .param("group_by", "model")
                        .param("project_id", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.group_value=='gpt-4o')]").exists())
                .andExpect(jsonPath("$.data[?(@.group_value=='gemini-2.0-flash')]").exists())
                .andExpect(jsonPath("$.data[?(@.group_value=='gemini-2.5-flash-image')]").exists());

        mockMvc.perform(get("/costs/summary")
                        .param("group_by", "project")
                        .param("organization_id", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].group_value").value(projectId))
                .andExpect(jsonPath("$.data[0].estimated_cost_micros_usd").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/costs/timeseries")
                        .param("bucket", "day")
                        .param("group_by", "provider")
                        .param("organization_id", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bucket").exists())
                .andExpect(jsonPath("$.data[0].group_value").exists())
                .andExpect(jsonPath("$.data[0].estimated_cost_micros_usd").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/costs/summary")
                        .param("group_by", "provider")
                        .param("organization_id", organizationId)
                        .param("path", "/v1/images/generations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].group_value").value("google"));
    }

    @Test
    void auditLogsCaptureControlPlaneMutations() throws Exception {
        MvcResult orgResult = mockMvc.perform(post("/orgs")
                        .header("X-Correlation-Id", "audit-corr-1")
                        .header("X-Actor-Type", "admin")
                        .header("X-Actor-Id", "tester")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Audit Org"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String organizationId = read(orgResult, "/id");

        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "name":"Audit Project"
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = read(projectResult, "/id");

        MvcResult keyResult = mockMvc.perform(post("/keys/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "projectId":"%s",
                                  "name":"Audit Key"
                                }
                                """.formatted(organizationId, projectId)))
                .andExpect(status().isOk())
                .andReturn();
        String apiKeyId = read(keyResult, "/id");

        MvcResult ruleResult = mockMvc.perform(post("/keys/api/" + apiKeyId + "/iam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rule_type":"MODEL",
                                  "effect":"ALLOW",
                                  "pattern":"gpt-4o"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String ruleId = read(ruleResult, "/id");

        mockMvc.perform(patch("/projects/" + projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Audit Project Renamed"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/keys/api/" + apiKeyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"budget_micros_usd":12345}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/keys/api/" + apiKeyId + "/iam/" + ruleId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/audit-logs/" + organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.action=='organization.create')]").exists())
                .andExpect(jsonPath("$.data[?(@.action=='project.create')]").exists())
                .andExpect(jsonPath("$.data[?(@.action=='api_key.create')]").exists())
                .andExpect(jsonPath("$.data[?(@.action=='iam_rule.create')]").exists())
                .andExpect(jsonPath("$.data[?(@.action=='project.update')]").exists())
                .andExpect(jsonPath("$.data[?(@.action=='api_key.update')]").exists())
                .andExpect(jsonPath("$.data[?(@.action=='iam_rule.delete')]").exists())
                .andExpect(jsonPath("$.data[?(@.correlation_id=='audit-corr-1')]").exists())
                .andExpect(jsonPath("$.data[?(@.actor_type=='admin' && @.actor_id=='tester')]").exists());

        mockMvc.perform(get("/audit-logs/" + organizationId + "/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actions").isArray())
                .andExpect(jsonPath("$.data.resource_types").isArray())
                .andExpect(jsonPath("$.data.actor_types[0]").value("admin"));
    }

    @Test
    void modelSyncEndpointCanImportProviderModels() throws Exception {
        OPENAI_RESPONSES.add(json("""
                {
                  "data": [
                    {"id": "gpt-4.1-mini"},
                    {"id": "gpt-image-1"}
                  ]
                }
                """));

        mockMvc.perform(post("/internal/models/sync")
                        .param("provider", "openai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced_mappings").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='gpt-4.1-mini')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='gpt-image-1')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='gpt-4.1-mini' && @.context_window_tokens==1000000)]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='gpt-4.1-mini' && @.input_cost_micros_per_token==4)]").exists());

        OPENAI_RESPONSES.add(json("""
                {
                  "data": [
                    {"id": "gpt-4.1-mini"}
                  ]
                }
                """));

        mockMvc.perform(post("/internal/models/sync")
                        .param("provider", "openai"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='gpt-4.1-mini')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='gpt-image-1')]").doesNotExist());

        mockMvc.perform(get("/internal/models/sync/history")
                        .param("provider", "openai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].provider_id").value("openai"))
                .andExpect(jsonPath("$.data[0].archived_mappings").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void apiKeyRateLimitIsEnforced() throws Exception {
        MvcResult orgResult = mockMvc.perform(post("/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rate Org"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String organizationId = read(orgResult, "/id");

        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "name":"Rate Project"
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = read(projectResult, "/id");

        MvcResult keyResult = mockMvc.perform(post("/keys/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "projectId":"%s",
                                  "name":"Rate Key"
                                }
                                """.formatted(organizationId, projectId)))
                .andExpect(status().isOk())
                .andReturn();
        String apiKeyId = read(keyResult, "/id");
        String rawToken = read(keyResult, "/token");

        mockMvc.perform(post("/keys/api/" + apiKeyId + "/iam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rule_type":"RATE",
                                  "effect":"LIMIT",
                                  "pattern":"1"
                                }
                                """))
                .andExpect(status().isOk());

        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-rate-1",
                  "choices": [{"message": {"role": "assistant", "content": "rate first ok"}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "first request"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "second request"}
                                  ]
                                }
                                """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void guardrailsCanBlockRequestsAndRecordViolations() throws Exception {
        MvcResult orgResult = mockMvc.perform(post("/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Guardrail Org"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String organizationId = read(orgResult, "/id");

        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "name":"Guardrail Project"
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = read(projectResult, "/id");

        MvcResult keyResult = mockMvc.perform(post("/keys/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "projectId":"%s",
                                  "name":"Guardrail Key"
                                }
                                """.formatted(organizationId, projectId)))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken = read(keyResult, "/token");

        MvcResult ruleResult = mockMvc.perform(post("/guardrails/rules/" + organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Ban secrets",
                                  "rule_type":"KEYWORD",
                                  "pattern":"secret",
                                  "action":"BLOCK"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ban secrets"))
                .andExpect(jsonPath("$.action").value("BLOCK"))
                .andReturn();
        String ruleId = read(ruleResult, "/id");

        mockMvc.perform(get("/guardrails/system-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rule_type").value("KEYWORD"));

        mockMvc.perform(post("/guardrails/test/" + organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"this contains a secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.matched_rules[0]").value("Ban secrets"));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "show me the secret"}
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Guardrail blocked request")));

        mockMvc.perform(get("/guardrails/violations/" + organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rule_name").value("Ban secrets"))
                .andExpect(jsonPath("$.data[0].path").value("/v1/chat/completions"))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/guardrails/stats/" + organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violation_count").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.rule_names[0]").value("Ban secrets"));

        mockMvc.perform(patch("/guardrails/rules/" + organizationId + "/" + ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-guardrail-allowed",
                  "choices": [{"message": {"role": "assistant", "content": "rule disabled now"}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 4, "total_tokens": 14}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "show me the secret"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value("rule disabled now"));

        mockMvc.perform(delete("/guardrails/rules/" + organizationId + "/" + ruleId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/audit-logs/" + organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.action=='guardrail_rule.create')]").exists())
                .andExpect(jsonPath("$.data[?(@.action=='guardrail_rule.update')]").exists())
                .andExpect(jsonPath("$.data[?(@.action=='guardrail_rule.delete')]").exists());
    }

    @Test
    void adminMetricsEndpointsExposeDashboardData() throws Exception {
        MvcResult orgResult = mockMvc.perform(post("/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Admin Org"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String organizationId = read(orgResult, "/id");

        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "name":"Admin Project"
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = read(projectResult, "/id");

        MvcResult keyResult = mockMvc.perform(post("/keys/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "projectId":"%s",
                                  "name":"Admin Key"
                                }
                                """.formatted(organizationId, projectId)))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken = read(keyResult, "/token");

        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-admin",
                  "choices": [{"message": {"role": "assistant", "content": "admin metrics"}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "admin metrics"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/costs/recompute"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organization_count").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.request_count").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/admin/metrics/timeseries")
                        .param("bucket", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bucket").exists());

        mockMvc.perform(get("/admin/metrics/cost-by-model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.group_value=='gpt-4o')]").exists());

        mockMvc.perform(get("/admin/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + organizationId + "')]").exists());

        mockMvc.perform(get("/admin/organizations/" + organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(organizationId));

        mockMvc.perform(get("/admin/organizations/" + organizationId + "/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(projectId));

        mockMvc.perform(get("/admin/organizations/" + organizationId + "/projects/" + projectId + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request_count").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.models[0]").value("gpt-4o"));

        mockMvc.perform(get("/admin/organizations/" + organizationId + "/projects/" + projectId + "/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requested_model").value("gpt-4o"));
    }

    @Test
    void providerProbeEndpointUpdatesHealthState() throws Exception {
        OPENAI_RESPONSES.add(json("""
                {
                  "data": [
                    {"id": "gpt-4o"}
                  ]
                }
                """));

        mockMvc.perform(post("/internal/providers/probe")
                        .param("provider", "openai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider_id").value("openai"))
                .andExpect(jsonPath("$.data.healthy").value(true))
                .andExpect(jsonPath("$.data.discovered_models").value(1));

        mockMvc.perform(get("/internal/providers/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openai.healthy").value(true));
    }

    @Test
    void mcpEndpointsSupportOauthMetadataAndToolCalls() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint").exists())
                .andExpect(jsonPath("$.token_endpoint").exists());

        mockMvc.perform(get("/.well-known/oauth-authorization-server/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource", containsString("/mcp")));

        mockMvc.perform(get("/oauth/authorize")
                        .param("client_id", "mcp-client")
                        .param("state", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("mcp-auth-code"))
                .andExpect(jsonPath("$.state").value("abc"));

        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"mcp tools"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"));

        mockMvc.perform(post("/oauth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"client_name":"Gateway MCP Client"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").exists())
                .andExpect(jsonPath("$.client_name").value("Gateway MCP Client"));

        mockMvc.perform(get("/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools[?(@.name=='chat')]").exists())
                .andExpect(jsonPath("$.tools[?(@.name=='list-models')]").exists());

        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"tools/list"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools[?(@.name=='generate-image')]").exists());

        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-mcp",
                  "choices": [{"message": {"role": "assistant", "content": "MCP says hello"}}],
                  "usage": {"prompt_tokens": 9, "completion_tokens": 4, "total_tokens": 13}
                }
                """));

        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method":"tools/call",
                                  "name":"chat",
                                  "arguments":{"model":"gpt-4o","prompt":"hello from mcp"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").value("chat"))
                .andExpect(jsonPath("$.result.choices[0].message.content").value("MCP says hello"));

        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"inlineData": {"data": "TUNQSU1BR0U="}}]
                    }
                  }]
                }
                """));

        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method":"tools/call",
                                  "name":"generate-image",
                                  "arguments":{"model":"gemini-2.5-flash-image","prompt":"banana"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").value("generate-image"))
                .andExpect(jsonPath("$.result.data[0].b64_json").value("TUNQSU1BR0U="));
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

    private MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private String read(MvcResult result, String pointer) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode node = root.at(pointer);
        return node.asText();
    }
}
