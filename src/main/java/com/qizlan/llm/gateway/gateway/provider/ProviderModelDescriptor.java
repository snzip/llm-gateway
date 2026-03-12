package com.qizlan.llm.gateway.gateway.provider;

public record ProviderModelDescriptor(
        String providerId,
        String providerModelName,
        String gatewayModelId,
        String displayName,
        String family,
        boolean supportsStreaming,
        boolean supportsVision,
        boolean supportsTools,
        boolean supportsReasoning,
        boolean imageGeneration,
        int priority,
        int contextWindowTokens,
        long inputCostMicrosPerToken,
        long outputCostMicrosPerToken
) {
}
