package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;

class ProviderProbeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void providerProbeEndpointsReturnHealthAndHistory() {
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
                .jsonPath("$.data[0].provider_id").isEqualTo("openai")
                .jsonPath("$.data[0].strategy").isEqualTo("synthetic_completion");
    }
}
