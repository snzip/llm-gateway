package com.qizlan.llm.gateway.gateway.provider;

public record ProviderChatResult(
        String providerId,
        String modelName,
        String content,
        boolean imageResponse,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
}
