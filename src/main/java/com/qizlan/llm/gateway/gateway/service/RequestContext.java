package com.qizlan.llm.gateway.gateway.service;

public record RequestContext(String correlationId, String traceId, String spanId, String actorType, String actorId) {
}
