package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class SwaggerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void openApiDocumentExposesBearerSecurityAndTags() {
        webTestClient.get().uri("/json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.securitySchemes.BearerAuth.type").isEqualTo("http")
                .jsonPath("$.components.securitySchemes.BearerAuth.scheme").isEqualTo("bearer")
                .jsonPath("$.tags[?(@.name=='Data Plane')]").exists()
                .jsonPath("$.tags[?(@.name=='Control Plane')]").exists()
                .jsonPath("$.tags[?(@.name=='MCP')]").exists();
    }
}
