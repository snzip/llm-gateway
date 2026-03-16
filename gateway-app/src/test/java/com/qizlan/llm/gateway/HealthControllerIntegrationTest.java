package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;

class HealthControllerIntegrationTest extends BaseGatewayTest {

    @Test
    void healthEndpointWorks() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.health.status").isEqualTo("UP");
    }

    @Test
    void metricsEndpointIncludesGatewayMetrics() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    org.junit.jupiter.api.Assertions.assertTrue(body.contains("llm.gateway.http.requests"));
                    org.junit.jupiter.api.Assertions.assertTrue(body.contains("path=\"/\""));
                });
    }
}
