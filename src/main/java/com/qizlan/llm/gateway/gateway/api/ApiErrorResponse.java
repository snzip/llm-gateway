package com.qizlan.llm.gateway.gateway.api;

public record ApiErrorResponse(
        boolean error,
        int status,
        String message
) {
}
