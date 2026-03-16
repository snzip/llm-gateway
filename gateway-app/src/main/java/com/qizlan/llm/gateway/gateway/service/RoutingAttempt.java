package com.qizlan.llm.gateway.gateway.service;

public record RoutingAttempt(
        String provider,
        String model,
        int statusCode,
        String errorType,
        boolean succeeded
) {
}
