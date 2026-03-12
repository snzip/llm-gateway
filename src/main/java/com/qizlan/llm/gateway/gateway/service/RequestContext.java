package com.qizlan.llm.gateway.gateway.service;

public record RequestContext(String correlationId, String actorType, String actorId) {
}
