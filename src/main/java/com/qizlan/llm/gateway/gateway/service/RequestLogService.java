package com.qizlan.llm.gateway.gateway.service;

import io.micrometer.tracing.Tracer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionResponse;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.RequestLogEntity;
import com.qizlan.llm.gateway.persistence.repository.ApiKeyRepository;
import com.qizlan.llm.gateway.persistence.repository.RequestLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

@Service
public class RequestLogService {

    private final RequestLogRepository requestLogRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper;
    private final GatewayMetricsService gatewayMetricsService;
    private final RequestLogPayloadSanitizer payloadSanitizer;
    private final GatewayProperties properties;
    private final Tracer tracer;

    public RequestLogService(RequestLogRepository requestLogRepository, ApiKeyRepository apiKeyRepository, ObjectMapper objectMapper, GatewayMetricsService gatewayMetricsService, RequestLogPayloadSanitizer payloadSanitizer, GatewayProperties properties, Tracer tracer) {
        this.requestLogRepository = requestLogRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.objectMapper = objectMapper;
        this.gatewayMetricsService = gatewayMetricsService;
        this.payloadSanitizer = payloadSanitizer;
        this.properties = properties;
        this.tracer = tracer;
    }

    public void logGatewayRequest(
            String path,
            String requestedModel,
            String providerId,
            ApiKeyEntity apiKey,
            ChatCompletionResponse.Usage usage,
            int httpStatus,
            long latencyMs,
            List<RoutingAttempt> routingAttempts,
            Object requestPayload,
            Object responsePayload,
            RequestContext requestContext
    ) {
        boolean sampled = shouldSample();
        int promptTokens = usage == null ? 0 : usage.prompt_tokens();
        int completionTokens = usage == null ? 0 : usage.completion_tokens();
        int totalTokens = usage == null ? 0 : usage.total_tokens();
        int reasoningTokens = usage == null || usage.reasoning_tokens() == null ? 0 : usage.reasoning_tokens();
        CostBreakdown cost = estimateCostMicrosUsd(requestedModel, promptTokens, completionTokens);
        RequestLogEntity log = new RequestLogEntity(
                nextRequestId(),
                path,
                requestedModel,
                providerId,
                httpStatus,
                latencyMs,
                promptTokens,
                completionTokens,
                totalTokens,
                cost.totalMicrosUsd(),
                cost.promptMicrosUsd(),
                cost.completionMicrosUsd(),
                reasoningTokens,
                0,
                0,
                0,
                false,
                false,
                routingAttempts.size() > 1,
                httpStatus >= 400,
                apiKey == null ? null : apiKey.getId(),
                apiKey == null || apiKey.getOrganization() == null ? null : apiKey.getOrganization().getId(),
                apiKey == null || apiKey.getProject() == null ? null : apiKey.getProject().getId(),
                writeTrace(routingAttempts),
                requestContext == null ? "" : requestContext.correlationId(),
                traceId(requestContext),
                spanId(requestContext),
                sampledPayload(requestPayload, sampled),
                sampledResponse(responsePayload, sampled)
        );
        requestLogRepository.save(log);
        gatewayMetricsService.recordGatewayRequest(
                path,
                providerId,
                requestedModel,
                httpStatus,
                latencyMs,
                promptTokens,
                completionTokens,
                totalTokens,
                cost.totalMicrosUsd(),
                false
        );
        if (apiKey != null) {
            apiKey.addSpentMicrosUsd(cost.totalMicrosUsd());
            apiKeyRepository.save(apiKey);
        }
    }

    public void logImageRequest(String path, String requestedModel, String providerId, ApiKeyEntity apiKey, int httpStatus, long latencyMs, List<RoutingAttempt> routingAttempts, Object requestPayload, Object responsePayload, RequestContext requestContext) {
        boolean sampled = shouldSample();
        long estimatedCost = estimateImageCostMicrosUsd(requestedModel);
        RequestLogEntity log = new RequestLogEntity(
                nextRequestId(),
                path,
                requestedModel,
                providerId,
                httpStatus,
                latencyMs,
                0,
                0,
                0,
                estimatedCost,
                estimatedCost,
                0,
                0,
                0,
                1,
                0,
                false,
                false,
                routingAttempts.size() > 1,
                httpStatus >= 400,
                apiKey == null ? null : apiKey.getId(),
                apiKey == null || apiKey.getOrganization() == null ? null : apiKey.getOrganization().getId(),
                apiKey == null || apiKey.getProject() == null ? null : apiKey.getProject().getId(),
                writeTrace(routingAttempts),
                requestContext == null ? "" : requestContext.correlationId(),
                traceId(requestContext),
                spanId(requestContext),
                sampledPayload(requestPayload, sampled),
                sampledResponse(responsePayload, sampled)
        );
        requestLogRepository.save(log);
        gatewayMetricsService.recordGatewayRequest(path, providerId, requestedModel, httpStatus, latencyMs, 0, 0, 0, estimatedCost, false);
    }

    public void logStreamRequest(
            String path,
            String requestedModel,
            String providerId,
            ApiKeyEntity apiKey,
            long httpStatus,
            long latencyMs,
            long timeToFirstTokenMs,
            List<RoutingAttempt> routingAttempts,
            Object requestPayload,
            RequestContext requestContext
    ) {
        boolean sampled = shouldSample();
        RequestLogEntity log = new RequestLogEntity(
                nextRequestId(),
                path,
                requestedModel,
                providerId,
                (int) httpStatus,
                latencyMs,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                timeToFirstTokenMs,
                true,
                false,
                routingAttempts.size() > 1,
                httpStatus >= 400,
                apiKey == null ? null : apiKey.getId(),
                apiKey == null || apiKey.getOrganization() == null ? null : apiKey.getOrganization().getId(),
                apiKey == null || apiKey.getProject() == null ? null : apiKey.getProject().getId(),
                writeTrace(routingAttempts),
                requestContext == null ? "" : requestContext.correlationId(),
                traceId(requestContext),
                spanId(requestContext),
                sampledPayload(requestPayload, sampled),
                ""
        );
        requestLogRepository.save(log);
        gatewayMetricsService.recordGatewayRequest(path, providerId, requestedModel, (int) httpStatus, latencyMs, 0, 0, 0, 0, true);
    }

    public void logGatewayFailure(String path, String requestedModel, ApiKeyEntity apiKey, int httpStatus, long latencyMs, List<RoutingAttempt> routingAttempts, Object requestPayload, RequestContext requestContext) {
        boolean sampled = shouldSample();
        RequestLogEntity log = new RequestLogEntity(
                nextRequestId(),
                path,
                requestedModel,
                routingAttempts.isEmpty() ? "none" : routingAttempts.get(routingAttempts.size() - 1).provider(),
                httpStatus,
                latencyMs,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                routingAttempts.size() > 1,
                true,
                apiKey == null ? null : apiKey.getId(),
                apiKey == null || apiKey.getOrganization() == null ? null : apiKey.getOrganization().getId(),
                apiKey == null || apiKey.getProject() == null ? null : apiKey.getProject().getId(),
                writeTrace(routingAttempts),
                requestContext == null ? "" : requestContext.correlationId(),
                traceId(requestContext),
                spanId(requestContext),
                sampledPayload(requestPayload, sampled),
                ""
        );
        requestLogRepository.save(log);
        gatewayMetricsService.recordGatewayRequest(
                path,
                routingAttempts.isEmpty() ? "none" : routingAttempts.get(routingAttempts.size() - 1).provider(),
                requestedModel,
                httpStatus,
                latencyMs,
                0,
                0,
                0,
                0,
                false
        );
    }

    public List<RequestLogEntity> list() {
        return requestLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public RequestLogEntity get(String id) {
        return requestLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown log: " + id));
    }

    private CostBreakdown estimateCostMicrosUsd(String model, int promptTokens, int completionTokens) {
        if (model == null) {
            return new CostBreakdown(0L, 0L, 0L);
        }
        if (model.startsWith("gpt-4o")) {
            return new CostBreakdown(promptTokens * 5L, completionTokens * 15L, (promptTokens * 5L) + (completionTokens * 15L));
        }
        if (model.startsWith("claude")) {
            return new CostBreakdown(promptTokens * 6L, completionTokens * 18L, (promptTokens * 6L) + (completionTokens * 18L));
        }
        if (model.startsWith("gateway-text")) {
            return new CostBreakdown(promptTokens * 6L, completionTokens * 18L, (promptTokens * 6L) + (completionTokens * 18L));
        }
        if (model.startsWith("gemini")) {
            return new CostBreakdown(promptTokens * 2L, completionTokens * 6L, (promptTokens * 2L) + (completionTokens * 6L));
        }
        return new CostBreakdown(0L, 0L, 0L);
    }

    private long estimateImageCostMicrosUsd(String model) {
        if (model != null && model.contains("image")) {
            return 25_000L;
        }
        return 10_000L;
    }

    private String writeTrace(List<RoutingAttempt> routingAttempts) {
        try {
            return objectMapper.writeValueAsString(routingAttempts);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String nextRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String sampledPayload(Object payload, boolean sampled) {
        if (!properties.requestLog().payloadSamplingEnabled() || !sampled) {
            return "";
        }
        return payloadSanitizer.sanitize(payload);
    }

    private String sampledResponse(Object payload, boolean sampled) {
        if (!properties.requestLog().payloadSamplingEnabled() || !properties.requestLog().storeResponsePayload() || !sampled) {
            return "";
        }
        return payloadSanitizer.sanitize(payload);
    }

    private boolean shouldSample() {
        double rate = properties.requestLog().sampleRate();
        if (rate >= 1.0d) {
            return true;
        }
        if (rate <= 0.0d) {
            return false;
        }
        return Math.random() <= rate;
    }

    private String traceId(RequestContext requestContext) {
        if (tracer.currentSpan() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return requestContext == null ? "" : requestContext.traceId();
    }

    private String spanId(RequestContext requestContext) {
        if (tracer.currentSpan() != null) {
            return tracer.currentSpan().context().spanId();
        }
        return requestContext == null ? "" : requestContext.spanId();
    }

    private record CostBreakdown(long promptMicrosUsd, long completionMicrosUsd, long totalMicrosUsd) {
    }
}
