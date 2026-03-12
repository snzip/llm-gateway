package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionResponse;
import com.qizlan.llm.gateway.gateway.provider.UpstreamProviderException;
import com.qizlan.llm.gateway.gateway.provider.ProviderChatResult;
import com.qizlan.llm.gateway.gateway.provider.ProviderGateway;
import com.qizlan.llm.gateway.gateway.provider.ProviderStreamEvent;
import com.qizlan.llm.gateway.gateway.provider.ProviderStreamFormat;
import com.qizlan.llm.gateway.gateway.service.GuardrailService;
import com.qizlan.llm.gateway.gateway.security.IamRuleService;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class ChatGatewayService {

    private final RoutingService routingService;
    private final ProviderGateway providerGateway;
    private final RequestLogService requestLogService;
    private final ProviderHealthService providerHealthService;
    private final IamRuleService iamRuleService;
    private final GuardrailService guardrailService;

    public ChatGatewayService(RoutingService routingService, ProviderGateway providerGateway, RequestLogService requestLogService, ProviderHealthService providerHealthService, IamRuleService iamRuleService, GuardrailService guardrailService) {
        this.routingService = routingService;
        this.providerGateway = providerGateway;
        this.requestLogService = requestLogService;
        this.providerHealthService = providerHealthService;
        this.iamRuleService = iamRuleService;
        this.guardrailService = guardrailService;
    }

    public ChatCompletionResponse complete(ChatCompletionRequest request, ApiKeyEntity apiKey) {
        long startedAt = System.currentTimeMillis();
        guardrailService.evaluate(
                apiKey == null || apiKey.getOrganization() == null ? "" : apiKey.getOrganization().getId(),
                request.messages().stream().map(message -> message.content() == null ? "" : message.content().toString()).reduce((a, b) -> a + "\n" + b).orElse(""),
                "/v1/chat/completions",
                apiKey
        );
        iamRuleService.assertModelAllowed(apiKey, request.model());
        List<ModelProviderMappingEntity> candidates = routingService.resolveCandidates(request.model(), apiKey);
        if (candidates.isEmpty()) {
            throw new com.qizlan.llm.gateway.gateway.security.ApiKeyAccessDeniedException("Provider access denied for model: " + request.model());
        }
        List<RoutingAttempt> attempts = new ArrayList<>();
        RuntimeException lastError = null;
        for (ModelProviderMappingEntity mapping : candidates) {
            try {
                ProviderChatResult result = providerGateway.complete(request, mapping.getProvider().getId(), mapping.getModelName());
                providerHealthService.recordSuccess(mapping.getProvider().getId());
                attempts.add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 200, "none", true));
                ChatCompletionResponse response = ChatCompletionResponse.of(
                        mapping.getModel().getId(),
                        result.providerId(),
                        request.model(),
                        result.content(),
                        result.imageResponse(),
                        result.promptTokens(),
                        result.completionTokens(),
                        result.totalTokens(),
                        toRoutingMetadata(attempts)
                );
                requestLogService.logGatewayRequest("/v1/chat/completions", request.model(), result.providerId(), apiKey, response.usage(), 200, System.currentTimeMillis() - startedAt, attempts);
                return response;
            } catch (UpstreamProviderException ex) {
                providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
                attempts.add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), ex.getStatusCode(), "upstream_error", false));
                lastError = ex;
            } catch (RuntimeException ex) {
                providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
                attempts.add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 500, "gateway_error", false));
                lastError = ex;
            }
        }
        requestLogService.logGatewayFailure("/v1/chat/completions", request.model(), apiKey, 502, System.currentTimeMillis() - startedAt, attempts);
        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalArgumentException("No provider candidates available for model: " + request.model());
    }

    public void stream(ChatCompletionRequest request, ApiKeyEntity apiKey, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer) {
        guardrailService.evaluate(
                apiKey == null || apiKey.getOrganization() == null ? "" : apiKey.getOrganization().getId(),
                request.messages().stream().map(message -> message.content() == null ? "" : message.content().toString()).reduce((a, b) -> a + "\n" + b).orElse(""),
                format == ProviderStreamFormat.ANTHROPIC ? "/v1/messages" : "/v1/chat/completions",
                apiKey
        );
        iamRuleService.assertModelAllowed(apiKey, request.model());
        List<ModelProviderMappingEntity> candidates = routingService.resolveCandidates(request.model(), apiKey);
        if (candidates.isEmpty()) {
            throw new com.qizlan.llm.gateway.gateway.security.ApiKeyAccessDeniedException("Provider access denied for model: " + request.model());
        }
        RuntimeException lastError = null;
        for (ModelProviderMappingEntity mapping : candidates) {
            try {
                providerGateway.streamChat(request, mapping.getProvider().getId(), mapping.getModelName(), format, consumer);
                providerHealthService.recordSuccess(mapping.getProvider().getId());
                return;
            } catch (UpstreamProviderException ex) {
                providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
                lastError = ex;
            } catch (RuntimeException ex) {
                providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
                lastError = ex;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalArgumentException("No provider candidates available for model: " + request.model());
    }

    private List<Map<String, Object>> toRoutingMetadata(List<RoutingAttempt> attempts) {
        return attempts.stream()
                .map(attempt -> Map.<String, Object>of(
                        "provider", attempt.provider(),
                        "model", attempt.model(),
                        "status_code", attempt.statusCode(),
                        "error_type", attempt.errorType(),
                        "succeeded", attempt.succeeded()))
                .toList();
    }
}
