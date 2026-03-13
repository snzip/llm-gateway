package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.gateway.provider.ProviderModelDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ModelSyncIntegrationTest extends BaseGatewayTest {

    @Test
    void modelSyncEndpointsReturnSyncAndHistoryData() {
        List<ProviderModelDescriptor> descriptors = List.of(
                new ProviderModelDescriptor("openai", "gpt-4.1-mini", "gpt-4.1-mini", "GPT-4.1 Mini", "gpt", true, false, false, true, false, 1, 4096, 2, 2),
                new ProviderModelDescriptor("openai", "gpt-image-1", "gpt-image-1", "GPT Image 1", "image", false, true, false, false, true, 2, 4096, 5, 5)
        );
        mockProviderAdapter.enqueueModelList(descriptors);

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
