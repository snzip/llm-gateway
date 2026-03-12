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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
        return generateAsync(request, apiKey, new RequestContext("", "", "", "system", "sync")).block();
    }

    public Mono<ImageDtos.ImageResponse> generateAsync(ImageDtos.ImageGenerationRequest request, ApiKeyEntity apiKey) {
        return generateAsync(request, apiKey, new RequestContext("", "", "", "system", "sync"));
    }

    public Mono<ImageDtos.ImageResponse> generateAsync(ImageDtos.ImageGenerationRequest request, ApiKeyEntity apiKey, RequestContext requestContext) {
        return Mono.fromCallable(() -> prepareRequest(request.model(), request.prompt(), apiKey, "/v1/images/generations"))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(state -> tryGenerate(state.withContext(requestContext), request, 0))
                .contextWrite(ctx -> ctx.put(RequestContextService.REACTOR_CONTEXT_KEY, requestContext));
    }

    public ImageDtos.ImageResponse edit(ImageDtos.ImageEditRequest request, ApiKeyEntity apiKey) {
        return editAsync(request, apiKey, new RequestContext("", "", "", "system", "sync")).block();
    }

    public Mono<ImageDtos.ImageResponse> editAsync(ImageDtos.ImageEditRequest request, ApiKeyEntity apiKey) {
        return editAsync(request, apiKey, new RequestContext("", "", "", "system", "sync"));
    }

    public Mono<ImageDtos.ImageResponse> editAsync(ImageDtos.ImageEditRequest request, ApiKeyEntity apiKey, RequestContext requestContext) {
        return Mono.fromCallable(() -> prepareRequest(request.model(), request.prompt(), apiKey, "/v1/images/edits"))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(state -> tryEdit(state.withContext(requestContext), request, 0))
                .contextWrite(ctx -> ctx.put(RequestContextService.REACTOR_CONTEXT_KEY, requestContext));
    }

    private Mono<ImageDtos.ImageResponse> tryGenerate(ImageExecutionState state, ImageDtos.ImageGenerationRequest request, int index) {
        if (index >= state.candidates().size()) {
            requestLogService.logGatewayFailure(state.path(), state.requestedModel(), state.apiKey(), 502, System.currentTimeMillis() - state.startedAt(), state.attempts(), request, state.requestContext());
            return Mono.error(UpstreamProviderException.fromStatus("routing", 502, "All providers failed for model: " + state.requestedModel()));
        }
        ModelProviderMappingEntity mapping = state.candidates().get(index);
        return providerGateway.generateImageAsync(request, mapping.getProvider().getId(), mapping.getModelName())
                .doOnNext(response -> {
                    providerHealthService.recordSuccess(mapping.getProvider().getId());
                    state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 200, "none", true));
                    requestLogService.logImageRequest(state.path(), state.requestedModel(), mapping.getProvider().getId(), state.apiKey(), 200, System.currentTimeMillis() - state.startedAt(), state.attempts(), request, response, state.requestContext());
                })
                .onErrorResume(UpstreamProviderException.class, ex -> handleImageFailure(state, index, mapping, ex, () -> tryGenerate(state, request, index + 1)))
                .onErrorResume(ex -> ex instanceof RuntimeException && !(ex instanceof UpstreamProviderException), ex -> {
                    providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
                    state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 500, "gateway_error", false));
                    return tryGenerate(state, request, index + 1);
                });
    }

    private Mono<ImageDtos.ImageResponse> tryEdit(ImageExecutionState state, ImageDtos.ImageEditRequest request, int index) {
        if (index >= state.candidates().size()) {
            requestLogService.logGatewayFailure(state.path(), state.requestedModel(), state.apiKey(), 502, System.currentTimeMillis() - state.startedAt(), state.attempts(), request, state.requestContext());
            return Mono.error(UpstreamProviderException.fromStatus("routing", 502, "All providers failed for model: " + state.requestedModel()));
        }
        ModelProviderMappingEntity mapping = state.candidates().get(index);
        return providerGateway.editImageAsync(request, mapping.getProvider().getId(), mapping.getModelName())
                .doOnNext(response -> {
                    providerHealthService.recordSuccess(mapping.getProvider().getId());
                    state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 200, "none", true));
                    requestLogService.logImageRequest(state.path(), state.requestedModel(), mapping.getProvider().getId(), state.apiKey(), 200, System.currentTimeMillis() - state.startedAt(), state.attempts(), request, response, state.requestContext());
                })
                .onErrorResume(UpstreamProviderException.class, ex -> handleImageFailure(state, index, mapping, ex, () -> tryEdit(state, request, index + 1)))
                .onErrorResume(ex -> ex instanceof RuntimeException && !(ex instanceof UpstreamProviderException), ex -> {
                    providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
                    state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 500, "gateway_error", false));
                    return tryEdit(state, request, index + 1);
                });
    }

    private Mono<ImageDtos.ImageResponse> handleImageFailure(ImageExecutionState state, int index, ModelProviderMappingEntity mapping, UpstreamProviderException ex, java.util.function.Supplier<Mono<ImageDtos.ImageResponse>> next) {
        providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
        state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), ex.getStatusCode(), ex.getErrorType(), false));
        if (!ex.isRetryable()) {
            requestLogService.logGatewayFailure(state.path(), state.requestedModel(), state.apiKey(), ex.getGatewayStatus(), System.currentTimeMillis() - state.startedAt(), state.attempts(), state.requestedModel(), state.requestContext());
            return Mono.error(ex);
        }
        return backoff(ex, index + 1).then(next.get());
    }

    private ImageExecutionState prepareRequest(String model, String prompt, ApiKeyEntity apiKey, String path) {
        long startedAt = System.currentTimeMillis();
        guardrailService.evaluate(
                apiKey == null || apiKey.getOrganization() == null ? "" : apiKey.getOrganization().getId(),
                prompt,
                path,
                apiKey
        );
        List<RoutingAttempt> attempts = new ArrayList<>();
        iamRuleService.assertModelAllowed(apiKey, model);
        List<ModelProviderMappingEntity> candidates = routingService.resolveCandidates(model, apiKey);
        if (candidates.isEmpty()) {
            throw new com.qizlan.llm.gateway.gateway.security.ApiKeyAccessDeniedException("Provider access denied for model: " + model);
        }
        return new ImageExecutionState(apiKey, path, model, startedAt, candidates, attempts, new RequestContext("", "", "", "system", "gateway"));
    }

    private Mono<Long> backoff(UpstreamProviderException ex, int attemptNumber) {
        if (ex.getStatusCode() != 429) {
            return Mono.empty();
        }
        return Mono.delay(java.time.Duration.ofMillis(Math.min(50L * attemptNumber, 200L)));
    }

    private record ImageExecutionState(
            ApiKeyEntity apiKey,
            String path,
            String requestedModel,
            long startedAt,
            List<ModelProviderMappingEntity> candidates,
            List<RoutingAttempt> attempts,
            RequestContext requestContext
    ) {
        private ImageExecutionState withContext(RequestContext context) {
            return new ImageExecutionState(apiKey, path, requestedModel, startedAt, candidates, attempts, context);
        }
    }
}
