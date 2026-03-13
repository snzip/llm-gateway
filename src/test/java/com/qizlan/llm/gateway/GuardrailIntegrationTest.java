package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;

class GuardrailIntegrationTest extends BaseGatewayTest {

    @Test
    void guardrailEndpointsCanCreateBlockAndReportViolations() {
        String organizationId = createOrganization(uniqueName("Guardrail Org"));
        String projectId = createProject(organizationId, uniqueName("Guardrail Project"));
        String rawToken = read(createApiKey(organizationId, projectId, uniqueName("Guardrail Key")), "/token");

        webTestClient.post().uri("/guardrails/rules/" + organizationId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Block badword","rule_type":"KEYWORD","pattern":"badword","action":"BLOCK"}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/guardrails/rules/" + organizationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].name").isEqualTo("Block badword");

        webTestClient.post().uri("/guardrails/test/" + organizationId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"text":"contains badword"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.blocked").isEqualTo(true);

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + rawToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-4o","messages":[{"role":"user","content":"contains badword"}]}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.get().uri("/guardrails/violations/" + organizationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].organization_id").isEqualTo(organizationId);

        webTestClient.get().uri("/guardrails/stats/" + organizationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.violation_count").exists();

        webTestClient.get().uri("/guardrails/system-rules")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].rule_type").isEqualTo("KEYWORD");
    }
}
