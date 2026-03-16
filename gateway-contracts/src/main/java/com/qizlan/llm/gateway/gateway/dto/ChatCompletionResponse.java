package com.qizlan.llm.gateway.gateway.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChatCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices,
        Usage usage,
        Metadata metadata
) {
    public static ChatCompletionResponse of(
            String model,
            String provider,
            String requestedModel,
            String content,
            boolean imageResponse,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            List<Map<String, Object>> routing
    ) {
        ChoiceMessage message = new ChoiceMessage("assistant", content, null, null,
                imageResponse ? List.of(Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString("fake-image".getBytes())))) : null);
        return new ChatCompletionResponse(
                "chatcmpl_" + Long.toHexString(System.nanoTime()),
                "chat.completion",
                Instant.now().getEpochSecond(),
                model,
                List.of(new Choice(0, message, "stop")),
                new Usage(
                        promptTokens == null ? 0 : promptTokens,
                        completionTokens == null ? 0 : completionTokens,
                        totalTokens == null ? (promptTokens == null ? 0 : promptTokens) + (completionTokens == null ? 0 : completionTokens) : totalTokens,
                        null
                ),
                new Metadata(requestedModel, null, model, provider, model, routing)
        );
    }

    public record Choice(int index, ChoiceMessage message, String finish_reason) {
    }

    public record ChoiceMessage(
            String role,
            String content,
            String reasoning,
            List<Map<String, Object>> tool_calls,
            List<Map<String, Object>> images
    ) {
    }

    public record Usage(
            int prompt_tokens,
            int completion_tokens,
            int total_tokens,
            Integer reasoning_tokens
    ) {
    }

    public record Metadata(
            String requested_model,
            String requested_provider,
            String used_model,
            String used_provider,
            String underlying_used_model,
            List<Map<String, Object>> routing
    ) {
    }
}
