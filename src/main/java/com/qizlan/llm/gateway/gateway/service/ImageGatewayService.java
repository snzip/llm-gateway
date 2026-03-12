package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import com.qizlan.llm.gateway.gateway.provider.ProviderGateway;
import com.qizlan.llm.gateway.gateway.provider.UpstreamProviderException;
import com.qizlan.llm.gateway.gateway.service.GuardrailService;
import com.qizlan.llm.gateway.gateway.security.IamRuleService;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ImageGatewayService {

    private final RoutingService routingService;
    private final ProviderGateway providerGateway;
    private final RequestLogService requestLogService;
    private final ProviderHealthService providerHealthService;
    private final IamRuleService iamRuleService;
    private final GuardrailService guardrailService;

    public ImageGatewayService(RoutingService routingService, ProviderGateway providerGateway, RequestLogService requestLogService, ProviderHealthService providerHealthService, IamRuleService iamRuleService, GuardrailService guardrailService) {
        this.routingService = routingService;
        this.providerGateway = providerGateway;
        this.requestLogService = requestLogService;
        this.providerHealthService = providerHealthService;
        this.iamRuleService = iamRuleService;
        this.guardrailService = guardrailService;
    }

    public ImageDtos.ImageResponse generate(ImageDtos.ImageGenerationRequest request, ApiKeyEntity apiKey) {
        long startedAt = System.currentTimeMillis();
        guardrailService.evaluate(
                apiKey == null || apiKey.getOrganization() == null ? "" : apiKey.getOrganization().getId(),
                request.prompt(),
                "/v1/images/generations",
                apiKey
        );
        List<RoutingAttempt> attempts = new ArrayList<>();
        iamRuleService.assertModelAllowed(apiKey, request.model());
        List<ModelProviderMappingEntity> candidates = routingService.resolveCandidates(request.model(), apiKey);
        if (candidates.isEmpty()) {
            throw new com.qizlan.llm.gateway.gateway.security.ApiKeyAccessDeniedException("Provider access denied for model: " + request.model());
        }
        for (ModelProviderMappingEntity mapping : candidates) {
            try {
                ImageDtos.ImageResponse response = providerGateway.generateImage(request, mapping.getProvider().getId(), mapping.getModelName());
                providerHealthService.recordSuccess(mapping.getProvider().getId());
                attempts.add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 200, "none", true));
                requestLogService.logImageRequest("/v1/images/generations", request.model(), mapping.getProvider().getId(), apiKey, 200, System.currentTimeMillis() - startedAt, attempts);
                return response;
            } catch (UpstreamProviderException ex) {
                providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
                attempts.add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), ex.getStatusCode(), ex.getErrorType(), false));
                if (!ex.isRetryable()) {
                    requestLogService.logGatewayFailure("/v1/images/generations", request.model(), apiKey, ex.getGatewayStatus(), System.currentTimeMillis() - startedAt, attempts);
                    throw ex;
                }
                applyBackoffIfNeeded(ex, attempts.size());
            }
        }
        requestLogService.logGatewayFailure("/v1/images/generations", request.model(), apiKey, 502, System.currentTimeMillis() - startedAt, attempts);
        throw UpstreamProviderException.fromStatus("routing", 502, "All providers failed for model: " + request.model());
    }

    public ImageDtos.ImageResponse edit(ImageDtos.ImageEditRequest request, ApiKeyEntity apiKey) {
        long startedAt = System.currentTimeMillis();
        guardrailService.evaluate(
                apiKey == null || apiKey.getOrganization() == null ? "" : apiKey.getOrganization().getId(),
                request.prompt(),
                "/v1/images/edits",
                apiKey
        );
        List<RoutingAttempt> attempts = new ArrayList<>();
        iamRuleService.assertModelAllowed(apiKey, request.model());
        List<ModelProviderMappingEntity> candidates = routingService.resolveCandidates(request.model(), apiKey);
        if (candidates.isEmpty()) {
            throw new com.qizlan.llm.gateway.gateway.security.ApiKeyAccessDeniedException("Provider access denied for model: " + request.model());
        }
        for (ModelProviderMappingEntity mapping : candidates) {
            try {
                ImageDtos.ImageResponse response = providerGateway.editImage(request, mapping.getProvider().getId(), mapping.getModelName());
                providerHealthService.recordSuccess(mapping.getProvider().getId());
                attempts.add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 200, "none", true));
                requestLogService.logImageRequest("/v1/images/edits", request.model(), mapping.getProvider().getId(), apiKey, 200, System.currentTimeMillis() - startedAt, attempts);
                return response;
            } catch (UpstreamProviderException ex) {
                providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
                attempts.add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), ex.getStatusCode(), ex.getErrorType(), false));
                if (!ex.isRetryable()) {
                    requestLogService.logGatewayFailure("/v1/images/edits", request.model(), apiKey, ex.getGatewayStatus(), System.currentTimeMillis() - startedAt, attempts);
                    throw ex;
                }
                applyBackoffIfNeeded(ex, attempts.size());
            }
        }
        requestLogService.logGatewayFailure("/v1/images/edits", request.model(), apiKey, 502, System.currentTimeMillis() - startedAt, attempts);
        throw UpstreamProviderException.fromStatus("routing", 502, "All providers failed for model: " + request.model());
    }

    private void applyBackoffIfNeeded(UpstreamProviderException ex, int attemptNumber) {
        if (ex.getStatusCode() != 429) {
            return;
        }
        try {
            Thread.sleep(Math.min(50L * attemptNumber, 200L));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
