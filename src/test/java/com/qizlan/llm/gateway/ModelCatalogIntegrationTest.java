package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;

class ModelCatalogIntegrationTest extends AbstractIntegrationTest {

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
}
