package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

class ModelSyncIntegrationTest extends AbstractIntegrationTest {

    @Test
    void modelSyncEndpointsReturnSyncAndHistoryData() {
        OPENAI_RESPONSES.add(json("""
                {
                  "data": [
                    {"id": "gpt-4.1-mini"},
                    {"id": "gpt-image-1"}
                  ]
                }
                """));

        webTestClient.post().uri(uriBuilder -> uriBuilder.path("/internal/models/sync").queryParam("provider", "openai").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.synced_mappings").value(greaterThanOrEqualTo(1));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/internal/models/sync/history").queryParam("provider", "openai").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].detail").value(containsString("OVERRIDE_MANUAL"));
    }
}
