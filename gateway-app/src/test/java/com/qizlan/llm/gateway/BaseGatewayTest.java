package com.qizlan.llm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.gatewayapp.GatewayAppApplication;
import com.qizlan.llm.gateway.gateway.provider.MockProviderAdapter;
import com.qizlan.llm.gateway.gateway.provider.ProviderChatResult;
import com.qizlan.llm.gateway.gateway.service.ProviderHealthService;
import com.qizlan.llm.gateway.persistence.repository.RequestLogRepository;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = GatewayAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@TestPropertySource(properties = "spring.security.webflux.csrf.enabled=false")
@Import(TestSecurityConfig.class)
abstract class BaseGatewayTest {

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ProviderHealthService providerHealthService;

    @Autowired
    protected MockProviderAdapter mockProviderAdapter;

    @Autowired
    protected RequestLogRepository requestLogRepository;

    @Autowired
    private GatewayProperties gatewayProperties;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("llm.gateway.providers.mode", () -> "mock");
        registry.add("llm.gateway.providers.openai.enabled", () -> true);
        registry.add("llm.gateway.providers.anthropic.enabled", () -> true);
        registry.add("llm.gateway.providers.google.enabled", () -> true);
    }

    @BeforeEach
    void configureDefaultAuthHeader() {
        GatewayProperties.SeedProperties seed = gatewayProperties.seed();
        if (seed != null && seed.enabled()) {
            String apiKey = seed.apiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                webTestClient = webTestClient.mutate()
                        .defaultHeader(gatewayProperties.apiKeyHeader(), "Bearer " + apiKey)
                        .build();
            }
        }
    }

    @BeforeEach
    void clearMockResponses() {
        mockProviderAdapter.reset();
        ((java.util.Map<?, ?>) ReflectionTestUtils.getField(providerHealthService, "states")).clear();
    }

    protected String read(org.springframework.test.web.reactive.server.EntityExchangeResult<byte[]> result, String pointer) {
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

    protected ProviderChatResult mockResponse(String providerId, String model, String content) {
        return mockResponse(providerId, model, content, 10, 5, 15);
    }

    protected ProviderChatResult mockResponse(String providerId, String model, String content, int promptTokens, int completionTokens, int totalTokens) {
        return new ProviderChatResult(providerId, model, content, false, promptTokens, completionTokens, totalTokens);
    }
}
