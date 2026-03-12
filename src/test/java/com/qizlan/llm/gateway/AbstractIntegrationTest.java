package com.qizlan.llm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.gateway.service.ProviderHealthService;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
abstract class AbstractIntegrationTest {

    protected static final BlockingQueue<MockResponse> OPENAI_RESPONSES = new LinkedBlockingQueue<>();
    protected static final BlockingQueue<MockResponse> ANTHROPIC_RESPONSES = new LinkedBlockingQueue<>();
    protected static final BlockingQueue<MockResponse> GOOGLE_RESPONSES = new LinkedBlockingQueue<>();
    protected static final MockWebServer OPENAI = startServer(OPENAI_RESPONSES);
    protected static final MockWebServer ANTHROPIC = startServer(ANTHROPIC_RESPONSES);
    protected static final MockWebServer GOOGLE = startServer(GOOGLE_RESPONSES);

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ProviderHealthService providerHealthService;

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

    @BeforeEach
    void clearMockResponses() {
        OPENAI_RESPONSES.clear();
        ANTHROPIC_RESPONSES.clear();
        GOOGLE_RESPONSES.clear();
        ((java.util.Map<?, ?>) ReflectionTestUtils.getField(providerHealthService, "states")).clear();
    }

    protected String createOrganization(String name) {
        EntityExchangeResult<byte[]> result = webTestClient.post().uri("/orgs")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"%s\"}".formatted(name))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        return read(result, "/id");
    }

    protected String createProject(String organizationId, String name) {
        EntityExchangeResult<byte[]> result = webTestClient.post().uri("/projects")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"organizationId":"%s","name":"%s"}
                        """.formatted(organizationId, name))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        return read(result, "/id");
    }

    protected EntityExchangeResult<byte[]> createApiKey(String organizationId, String projectId, String name) {
        return webTestClient.post().uri("/keys/api")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"organizationId":"%s","projectId":"%s","name":"%s"}
                        """.formatted(organizationId, projectId, name))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
    }

    protected String read(EntityExchangeResult<byte[]> result, String pointer) {
        try {
            JsonNode root = objectMapper.readTree(result.getResponseBodyContent());
            JsonNode node = root.at(pointer);
            return node.isMissingNode() || node.isNull() ? "" : node.asText();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected String uniqueName(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    protected static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
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
            public MockResponse dispatch(RecordedRequest request) {
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

    protected static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        protected NamedByteArrayResource(String filename, byte[] byteArray) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
