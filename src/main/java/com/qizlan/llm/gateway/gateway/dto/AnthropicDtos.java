package com.qizlan.llm.gateway.gateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public final class AnthropicDtos {

    private AnthropicDtos() {
    }

    public record AnthropicRequest(
            @NotBlank String model,
            @NotEmpty List<@Valid AnthropicMessage> messages,
            @Min(1) int max_tokens,
            Object system,
            Double temperature,
            List<Map<String, Object>> tools,
            Boolean stream
    ) {
        public boolean streamEnabled() {
            return Boolean.TRUE.equals(stream);
        }
    }

    public record AnthropicMessage(
            @NotBlank String role,
            Object content,
            String tool_call_id,
            String name,
            List<Map<String, Object>> tool_calls,
            Map<String, Object> function_call
    ) {
    }

    public record AnthropicResponse(
            String id,
            String type,
            String role,
            String model,
            List<Map<String, Object>> content,
            String stop_reason,
            String stop_sequence,
            Map<String, Integer> usage
    ) {
    }
}
