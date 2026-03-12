package com.qizlan.llm.gateway.gateway.dto;

import java.util.List;

public record ModelDto(
        String id,
        String name,
        String family,
        boolean free,
        int context_window_tokens,
        long input_cost_micros_per_token,
        long output_cost_micros_per_token,
        Architecture architecture,
        List<ProviderSupport> providers
) {
    public record Architecture(
            List<String> input_modalities,
            List<String> output_modalities
    ) {
    }

    public record ProviderSupport(
            String providerId,
            String modelName,
            boolean streaming,
            boolean vision,
            boolean tools,
            boolean reasoning
    ) {
    }
}
