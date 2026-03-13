package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.gateway.provider.UpstreamProviderException;
import com.qizlan.llm.gateway.gateway.service.ModelRoutingCache;
import com.qizlan.llm.gateway.persistence.entity.ModelEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import com.qizlan.llm.gateway.persistence.entity.ProviderEntity;
import com.qizlan.llm.gateway.persistence.repository.ModelProviderMappingRepository;
import com.qizlan.llm.gateway.persistence.repository.ModelRepository;
import com.qizlan.llm.gateway.persistence.repository.ProviderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

class RoutingIntegrationTest extends BaseGatewayTest {

    @Autowired
    private ModelRepository modelRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ModelProviderMappingRepository mappingRepository;

    @Autowired
    private ModelRoutingCache modelRoutingCache;

    @BeforeEach
    void resetGatewayTextMappings() {
        ModelEntity gatewayText = modelRepository.findById("gateway-text")
                .orElseThrow(() -> new IllegalStateException("Missing gateway-text model"));
        ProviderEntity openai = providerRepository.findById("openai")
                .orElseThrow(() -> new IllegalStateException("Missing openai provider"));
        ProviderEntity anthropic = providerRepository.findById("anthropic")
                .orElseThrow(() -> new IllegalStateException("Missing anthropic provider"));
        ensureMapping(gatewayText, openai, "gpt-4o", 10);
        ensureMapping(gatewayText, anthropic, "claude-3-5-sonnet", 20);
        modelRoutingCache.evict("gateway-text");
    }

    private void ensureMapping(ModelEntity model, ProviderEntity provider, String modelName, int priority) {
        ModelProviderMappingEntity mapping = mappingRepository.findByModelIdOrderByPriorityAscProviderIdAsc(model.getId()).stream()
                .filter(candidate -> candidate.getProvider().getId().equals(provider.getId()))
                .findFirst()
                .orElse(null);
        if (mapping == null) {
            mapping = ModelProviderMappingEntity.of(model, provider, modelName, true, false, true, true, false, priority);
        } else {
            mapping.refresh(modelName, true, false, true, true, false, priority, true, mapping.isSyncManaged());
        }
        mappingRepository.save(mapping);
    }

    @Test
    void routingFallbackAndProviderHealthWork() {
        mockProviderAdapter.enqueueFailure("openai", new UpstreamProviderException("openai", 500, 500, "server_error", true, "openai down"));
        mockProviderAdapter.enqueueCompletionResponse("anthropic", mockResponse("anthropic", "model-1", "Anthropic fallback response", 9, 4, 13));
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

        mockProviderAdapter.enqueueCompletionResponse("anthropic", mockResponse("anthropic", "model-2", "Anthropic still primary while openai cools down", 7, 3, 10));

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
    }

    @Test
    void upstreamClientErrorDoesNotFallbackAndReturnsSameStatus() {
        mockProviderAdapter.enqueueFailure("openai", new UpstreamProviderException("openai", 400, 400, "gateway_error", false, "bad request"));
        mockProviderAdapter.enqueueCompletionResponse("anthropic", mockResponse("anthropic", "model-3", "should not fallback", 1, 1, 2));

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
    }

    @Test
    void upstreamRateLimitFallsBackToNextProvider() {
        mockProviderAdapter.enqueueFailure("openai", UpstreamProviderException.fromStatus("openai", 429, "rate limited"));
        mockProviderAdapter.enqueueCompletionResponse("anthropic", mockResponse("anthropic", "model-4", "Anthropic after 429", 9, 4, 13));

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
