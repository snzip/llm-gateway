package com.qizlan.llm.gateway.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionResponse;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.RequestLogEntity;
import com.qizlan.llm.gateway.persistence.repository.ApiKeyRepository;
import com.qizlan.llm.gateway.persistence.repository.RequestLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

@Service
public class RequestLogService {

    private final RequestLogRepository requestLogRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper;

    public RequestLogService(RequestLogRepository requestLogRepository, ApiKeyRepository apiKeyRepository, ObjectMapper objectMapper) {
        this.requestLogRepository = requestLogRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.objectMapper = objectMapper;
    }

    public void logGatewayRequest(
            String path,
            String requestedModel,
            String providerId,
            ApiKeyEntity apiKey,
            ChatCompletionResponse.Usage usage,
            int httpStatus,
            long latencyMs,
            List<RoutingAttempt> routingAttempts
    ) {
        int promptTokens = usage == null ? 0 : usage.prompt_tokens();
        int completionTokens = usage == null ? 0 : usage.completion_tokens();
        int totalTokens = usage == null ? 0 : usage.total_tokens();
        long estimatedCost = estimateCostMicrosUsd(requestedModel, promptTokens, completionTokens);
        RequestLogEntity log = new RequestLogEntity(
                path,
                requestedModel,
                providerId,
                httpStatus,
                latencyMs,
                promptTokens,
                completionTokens,
                totalTokens,
                estimatedCost,
                apiKey == null ? null : apiKey.getId(),
                apiKey == null || apiKey.getOrganization() == null ? null : apiKey.getOrganization().getId(),
                apiKey == null || apiKey.getProject() == null ? null : apiKey.getProject().getId(),
                writeTrace(routingAttempts)
        );
        requestLogRepository.save(log);
        if (apiKey != null) {
            apiKey.addSpentMicrosUsd(estimatedCost);
            apiKeyRepository.save(apiKey);
        }
    }

    public void logImageRequest(String path, String requestedModel, String providerId, ApiKeyEntity apiKey, int httpStatus, long latencyMs, List<RoutingAttempt> routingAttempts) {
        RequestLogEntity log = new RequestLogEntity(
                path,
                requestedModel,
                providerId,
                httpStatus,
                latencyMs,
                0,
                0,
                0,
                estimateImageCostMicrosUsd(requestedModel),
                apiKey == null ? null : apiKey.getId(),
                apiKey == null || apiKey.getOrganization() == null ? null : apiKey.getOrganization().getId(),
                apiKey == null || apiKey.getProject() == null ? null : apiKey.getProject().getId(),
                writeTrace(routingAttempts)
        );
        requestLogRepository.save(log);
    }

    public void logGatewayFailure(String path, String requestedModel, ApiKeyEntity apiKey, int httpStatus, long latencyMs, List<RoutingAttempt> routingAttempts) {
        RequestLogEntity log = new RequestLogEntity(
                path,
                requestedModel,
                routingAttempts.isEmpty() ? "none" : routingAttempts.get(routingAttempts.size() - 1).provider(),
                httpStatus,
                latencyMs,
                0,
                0,
                0,
                0,
                apiKey == null ? null : apiKey.getId(),
                apiKey == null || apiKey.getOrganization() == null ? null : apiKey.getOrganization().getId(),
                apiKey == null || apiKey.getProject() == null ? null : apiKey.getProject().getId(),
                writeTrace(routingAttempts)
        );
        requestLogRepository.save(log);
    }

    public List<RequestLogEntity> list() {
        return requestLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public RequestLogEntity get(String id) {
        return requestLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown log: " + id));
    }

    private long estimateCostMicrosUsd(String model, int promptTokens, int completionTokens) {
        if (model == null) {
            return 0L;
        }
        if (model.startsWith("gpt-4o")) {
            return (promptTokens * 5L) + (completionTokens * 15L);
        }
        if (model.startsWith("claude")) {
            return (promptTokens * 6L) + (completionTokens * 18L);
        }
        if (model.startsWith("gateway-text")) {
            return (promptTokens * 6L) + (completionTokens * 18L);
        }
        if (model.startsWith("gemini")) {
            return (promptTokens * 2L) + (completionTokens * 6L);
        }
        return 0L;
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
}
