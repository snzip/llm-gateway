package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;

class HealthControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void healthEndpointWorks() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.health.status").isEqualTo("UP");
    }
}
