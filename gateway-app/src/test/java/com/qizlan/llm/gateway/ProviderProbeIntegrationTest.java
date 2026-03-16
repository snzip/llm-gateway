package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.gateway.provider.ProviderModelDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderProbeIntegrationTest extends BaseGatewayTest {

    @Test
    void providerProbeEndpointsReturnHealthAndHistory() {
        List<ProviderModelDescriptor> descriptors = List.of(
                new ProviderModelDescriptor("openai", "gpt-4o", "gpt-4o", "GPT-4o", "gpt", true, false, false, true, false, 1, 8192, 3, 3)
        );
        mockProviderAdapter.enqueueModelList("openai", descriptors);
        mockProviderAdapter.enqueueCompletionResponse("openai", mockResponse("openai", "gpt-4o", "pong", 1, 1, 2));

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
                .jsonPath("$.data[0].provider_id").isEqualTo("openai")
                .jsonPath("$.data[0].strategy").isEqualTo("synthetic_completion");
    }
}
