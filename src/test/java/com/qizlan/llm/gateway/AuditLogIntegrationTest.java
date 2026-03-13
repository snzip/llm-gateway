package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

class AuditLogIntegrationTest extends BaseGatewayTest {

    @Test
    void auditLogCarriesExplicitRequestContextHeaders() {
        EntityExchangeResult<byte[]> result = webTestClient.post().uri("/orgs")
                .header("X-Correlation-Id", "corr-123")
                .header("X-Actor-Type", "user")
                .header("X-Actor-Id", "alice")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Context Org"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "corr-123")
                .expectBody()
                .returnResult();
        String organizationId = read(result, "/id");

        webTestClient.get().uri("/audit-logs/" + organizationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].correlation_id").isEqualTo("corr-123")
                .jsonPath("$.data[0].actor_type").isEqualTo("user")
                .jsonPath("$.data[0].actor_id").isEqualTo("alice")
                .jsonPath("$.data[0].action").isEqualTo("organization.create");

        webTestClient.get().uri("/audit-logs/" + organizationId + "/filters")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.actions").isArray();
    }
}
