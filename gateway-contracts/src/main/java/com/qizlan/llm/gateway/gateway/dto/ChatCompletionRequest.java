package com.qizlan.llm.gateway.gateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record ChatCompletionRequest(
        @NotBlank String model,
        @NotEmpty List<@Valid ChatMessageInput> messages,
        Double temperature,
        Integer max_tokens,
        Double top_p,
        Double frequency_penalty,
        Double presence_penalty,
        ResponseFormat response_format,
        Boolean stream,
        List<ToolDefinition> tools,
        Object tool_choice,
        String reasoning_effort,
        ReasoningInput reasoning,
        String effort,
        Boolean free_models_only,
        Boolean onboarding,
        Boolean no_reasoning,
        Map<String, Object> sensitive_word_check,
        ImageConfigInput image_config,
        Boolean web_search,
        List<PluginInput> plugins
) {
    public boolean streamEnabled() {
        return Boolean.TRUE.equals(stream);
    }

    public record ChatMessageInput(
            @NotBlank String role,
            Object content,
            String name,
            String tool_call_id,
            List<Map<String, Object>> tool_calls
    ) {
    }

    public record ResponseFormat(
            @NotBlank String type,
            Map<String, Object> json_schema
    ) {
    }

    public record ToolDefinition(
            @NotBlank String type,
            Map<String, Object> function,
            Map<String, Object> user_location,
            String search_context_size,
            Integer max_uses
    ) {
    }

    public record ReasoningInput(
            String effort,
            Integer max_tokens
    ) {
    }

    public record ImageConfigInput(
            String aspect_ratio,
            String image_size,
            Integer n,
            Integer seed,
            String output_format,
            Integer output_compression
    ) {
    }

    public record PluginInput(@NotBlank String id) {
    }
}
