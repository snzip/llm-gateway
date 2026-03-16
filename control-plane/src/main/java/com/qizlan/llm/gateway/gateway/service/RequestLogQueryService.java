package com.qizlan.llm.gateway.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.persistence.entity.RequestLogEntity;
import com.qizlan.llm.gateway.persistence.repository.RequestLogRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RequestLogQueryService {

    private final RequestLogRepository requestLogRepository;
    private final ObjectMapper objectMapper;

    public RequestLogQueryService(RequestLogRepository requestLogRepository, ObjectMapper objectMapper) {
        this.requestLogRepository = requestLogRepository;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listLogs() {
        return requestLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toLog)
                .toList();
    }

    public Map<String, Object> getLog(String id) {
        RequestLogEntity entity = requestLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown log: " + id));
        return toLog(entity);
    }

    private Map<String, Object> toLog(RequestLogEntity entity) {
        return Map.ofEntries(
                Map.entry("id", entity.getId()),
                Map.entry("request_id", entity.getRequestId()),
                Map.entry("path", entity.getPath()),
                Map.entry("requested_model", entity.getRequestedModel()),
                Map.entry("provider_id", entity.getProviderId()),
                Map.entry("http_status", entity.getHttpStatus()),
                Map.entry("latency_ms", entity.getLatencyMs()),
                Map.entry("time_to_first_token_ms", entity.getTimeToFirstTokenMs()),
                Map.entry("prompt_tokens", entity.getPromptTokens()),
                Map.entry("completion_tokens", entity.getCompletionTokens()),
                Map.entry("total_tokens", entity.getTotalTokens()),
                Map.entry("reasoning_tokens", entity.getReasoningTokens()),
                Map.entry("cached_tokens", entity.getCachedTokens()),
                Map.entry("image_count", entity.getImageCount()),
                Map.entry("estimated_cost_micros_usd", entity.getEstimatedCostMicrosUsd()),
                Map.entry("prompt_cost_micros_usd", entity.getPromptCostMicrosUsd()),
                Map.entry("completion_cost_micros_usd", entity.getCompletionCostMicrosUsd()),
                Map.entry("streamed", entity.isStreamed()),
                Map.entry("canceled", entity.isCanceled()),
                Map.entry("retried", entity.isRetried()),
                Map.entry("has_error", entity.isHasError()),
                Map.entry("api_key_id", entity.getApiKeyId() == null ? "" : entity.getApiKeyId()),
                Map.entry("organization_id", entity.getOrganizationId() == null ? "" : entity.getOrganizationId()),
                Map.entry("project_id", entity.getProjectId() == null ? "" : entity.getProjectId()),
                Map.entry("correlation_id", entity.getCorrelationId() == null ? "" : entity.getCorrelationId()),
                Map.entry("trace_id", entity.getTraceId() == null ? "" : entity.getTraceId()),
                Map.entry("span_id", entity.getSpanId() == null ? "" : entity.getSpanId()),
                Map.entry("request_payload", entity.getRequestPayload() == null ? "" : entity.getRequestPayload()),
                Map.entry("response_payload", entity.getResponsePayload() == null ? "" : entity.getResponsePayload()),
                Map.entry("routing_trace", parseRoutingTrace(entity.getRoutingTrace()))
        );
    }

    private Object parseRoutingTrace(String routingTrace) {
        if (routingTrace == null || routingTrace.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(routingTrace, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
