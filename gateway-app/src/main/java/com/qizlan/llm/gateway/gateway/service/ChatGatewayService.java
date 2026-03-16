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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ChatGatewayService {

    private final RoutingService routingService;
    private final ProviderGateway providerGateway;
    private final RequestLogWriteService requestLogWriteService;
    private final ProviderHealthService providerHealthService;
    private final IamRuleService iamRuleService;
    private final GuardrailService guardrailService;

    public ChatGatewayService(RoutingService routingService, ProviderGateway providerGateway, RequestLogWriteService requestLogWriteService, ProviderHealthService providerHealthService, IamRuleService iamRuleService, GuardrailService guardrailService) {
        this.routingService = routingService;
        this.providerGateway = providerGateway;
        this.requestLogWriteService = requestLogWriteService;
        this.providerHealthService = providerHealthService;
        this.iamRuleService = iamRuleService;
        this.guardrailService = guardrailService;
    }

    public ChatCompletionResponse complete(ChatCompletionRequest request, ApiKeyEntity apiKey) {
        return completeAsync(request, apiKey, new RequestContext("", "", "", "system", "sync")).block();
    }

    public Mono<ChatCompletionResponse> completeAsync(ChatCompletionRequest request, ApiKeyEntity apiKey) {
        return completeAsync(request, apiKey, new RequestContext("", "", "", "system", "sync"));
    }

    public Mono<ChatCompletionResponse> completeAsync(ChatCompletionRequest request, ApiKeyEntity apiKey, RequestContext requestContext) {
        return Mono.fromCallable(() -> prepareRequest(request, apiKey, "/v1/chat/completions"))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(state -> tryComplete(state.withContext(requestContext), 0))
                .contextWrite(ctx -> ctx.put(RequestContextService.REACTOR_CONTEXT_KEY, requestContext));
    }

    public void stream(ChatCompletionRequest request, ApiKeyEntity apiKey, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer) {
        streamFlux(request, apiKey, format, new RequestContext("", "", "", "system", "sync"))
                .doOnNext(consumer)
                .blockLast();
    }

    public Flux<ProviderStreamEvent> streamFlux(ChatCompletionRequest request, ApiKeyEntity apiKey, ProviderStreamFormat format) {
        return streamFlux(request, apiKey, format, new RequestContext("", "", "", "system", "sync"));
    }

    public Flux<ProviderStreamEvent> streamFlux(ChatCompletionRequest request, ApiKeyEntity apiKey, ProviderStreamFormat format, RequestContext requestContext) {
        String path = format == ProviderStreamFormat.ANTHROPIC ? "/v1/messages" : "/v1/chat/completions";
        return Mono.fromCallable(() -> prepareRequest(request, apiKey, path))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(state -> tryStream(state.withContext(requestContext), format, 0))
                .contextWrite(ctx -> ctx.put(RequestContextService.REACTOR_CONTEXT_KEY, requestContext));
    }

    private Mono<ChatCompletionResponse> tryComplete(RequestExecutionState state, int index) {
        if (index >= state.candidates().size()) {
            requestLogWriteService.logGatewayFailure(state.path(), state.request().model(), state.apiKey(), gatewayStatus(state.lastError()), System.currentTimeMillis() - state.startedAt(), state.attempts(), state.request(), state.requestContext());
            return state.lastError() != null
                    ? Mono.error(state.lastError())
                    : Mono.error(new IllegalArgumentException("No provider candidates available for model: " + state.request().model()));
        }
        ModelProviderMappingEntity mapping = state.candidates().get(index);
        return providerGateway.completeAsync(state.request(), mapping.getProvider().getId(), mapping.getModelName())
                .doOnNext(result -> providerHealthService.recordSuccess(mapping.getProvider().getId()))
                .map(result -> {
                    state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 200, "none", true));
                    ChatCompletionResponse response = ChatCompletionResponse.of(
                            mapping.getModel().getId(),
                            result.providerId(),
                            state.request().model(),
                            result.content(),
                            result.imageResponse(),
                            result.promptTokens(),
                            result.completionTokens(),
                            result.totalTokens(),
                            toRoutingMetadata(state.attempts())
                    );
                    requestLogWriteService.logGatewayRequest(state.path(), state.request().model(), result.providerId(), state.apiKey(), response.usage(), 200, System.currentTimeMillis() - state.startedAt(), state.attempts(), state.request(), response, state.requestContext());
                    return response;
                })
                .onErrorResume(UpstreamProviderException.class, ex -> handleCompleteProviderFailure(state, index, mapping, ex))
                .onErrorResume(ex -> ex instanceof RuntimeException && !(ex instanceof UpstreamProviderException), ex -> {
                    providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
                    state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 500, "gateway_error", false));
                    state.lastError((RuntimeException) ex);
                    return tryComplete(state, index + 1);
                });
    }

    private Mono<ChatCompletionResponse> handleCompleteProviderFailure(RequestExecutionState state, int index, ModelProviderMappingEntity mapping, UpstreamProviderException ex) {
        providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
        state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), ex.getStatusCode(), ex.getErrorType(), false));
        state.lastError(ex);
        if (!ex.isRetryable()) {
            requestLogWriteService.logGatewayFailure(state.path(), state.request().model(), state.apiKey(), ex.getGatewayStatus(), System.currentTimeMillis() - state.startedAt(), state.attempts(), state.request(), state.requestContext());
            return Mono.error(ex);
        }
        return backoff(ex, index + 1).then(tryComplete(state, index + 1));
    }

    private Flux<ProviderStreamEvent> tryStream(RequestExecutionState state, ProviderStreamFormat format, int index) {
        if (index >= state.candidates().size()) {
            requestLogWriteService.logGatewayFailure(state.path(), state.request().model(), state.apiKey(), gatewayStatus(state.lastError()), System.currentTimeMillis() - state.startedAt(), state.attempts(), state.request(), state.requestContext());
            return state.lastError() != null
                    ? Flux.error(state.lastError())
                    : Flux.error(new IllegalArgumentException("No provider candidates available for model: " + state.request().model()));
        }
        ModelProviderMappingEntity mapping = state.candidates().get(index);
        AtomicLong firstTokenAt = new AtomicLong(-1L);
        return providerGateway.streamChatAsync(state.request(), mapping.getProvider().getId(), mapping.getModelName(), format)
                .doOnNext(event -> {
                    if (!event.done() && firstTokenAt.get() < 0) {
                        firstTokenAt.compareAndSet(-1L, System.currentTimeMillis());
                    }
                })
                .doOnComplete(() -> {
                    providerHealthService.recordSuccess(mapping.getProvider().getId());
                    state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 200, "none", true));
                    requestLogWriteService.logStreamRequest(state.path(), state.request().model(), mapping.getProvider().getId(), state.apiKey(), 200, System.currentTimeMillis() - state.startedAt(), firstTokenAt.get() < 0 ? 0 : firstTokenAt.get() - state.startedAt(), state.attempts(), state.request(), state.requestContext());
                })
                .onErrorResume(UpstreamProviderException.class, ex -> handleStreamProviderFailure(state, format, index, mapping, ex))
                .onErrorResume(ex -> ex instanceof RuntimeException && !(ex instanceof UpstreamProviderException), ex -> {
                    providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
                    state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), 500, "gateway_error", false));
                    state.lastError((RuntimeException) ex);
                    return tryStream(state, format, index + 1);
                });
    }

    private Flux<ProviderStreamEvent> handleStreamProviderFailure(RequestExecutionState state, ProviderStreamFormat format, int index, ModelProviderMappingEntity mapping, UpstreamProviderException ex) {
        providerHealthService.recordFailure(mapping.getProvider().getId(), ex.getMessage());
        state.attempts().add(new RoutingAttempt(mapping.getProvider().getId(), mapping.getModelName(), ex.getStatusCode(), ex.getErrorType(), false));
        state.lastError(ex);
        if (!ex.isRetryable()) {
            requestLogWriteService.logGatewayFailure(state.path(), state.request().model(), state.apiKey(), ex.getGatewayStatus(), System.currentTimeMillis() - state.startedAt(), state.attempts(), state.request(), state.requestContext());
            return Flux.error(ex);
        }
        return backoff(ex, index + 1).thenMany(tryStream(state, format, index + 1));
    }

    private RequestExecutionState prepareRequest(ChatCompletionRequest request, ApiKeyEntity apiKey, String path) {
        long startedAt = System.currentTimeMillis();
        guardrailService.evaluate(
                apiKey == null || apiKey.getOrganization() == null ? "" : apiKey.getOrganization().getId(),
                request.messages().stream().map(message -> message.content() == null ? "" : message.content().toString()).reduce((a, b) -> a + "\n" + b).orElse(""),
                path,
                apiKey
        );
        iamRuleService.assertModelAllowed(apiKey, request.model());
        List<ModelProviderMappingEntity> candidates = routingService.resolveCandidates(request.model(), apiKey);
        if (candidates.isEmpty()) {
            throw new com.qizlan.llm.gateway.gateway.security.ApiKeyAccessDeniedException("Provider access denied for model: " + request.model());
        }
        return new RequestExecutionState(request, apiKey, path, startedAt, candidates, new ArrayList<>(), new RequestContext("", "", "", "system", "gateway"), null);
    }

    private Mono<Long> backoff(UpstreamProviderException ex, int attemptNumber) {
        if (ex.getStatusCode() != 429) {
            return Mono.empty();
        }
        return Mono.delay(java.time.Duration.ofMillis(Math.min(50L * attemptNumber, 200L)));
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

    private int gatewayStatus(RuntimeException lastError) {
        if (lastError instanceof UpstreamProviderException upstream) {
            return upstream.getGatewayStatus();
        }
        return 502;
    }

    private static final class RequestExecutionState {
        private final ChatCompletionRequest request;
        private final ApiKeyEntity apiKey;
        private final String path;
        private final long startedAt;
        private final List<ModelProviderMappingEntity> candidates;
        private final List<RoutingAttempt> attempts;
        private final RequestContext requestContext;
        private RuntimeException lastError;

        private RequestExecutionState(ChatCompletionRequest request, ApiKeyEntity apiKey, String path, long startedAt, List<ModelProviderMappingEntity> candidates, List<RoutingAttempt> attempts, RequestContext requestContext, RuntimeException lastError) {
            this.request = request;
            this.apiKey = apiKey;
            this.path = path;
            this.startedAt = startedAt;
            this.candidates = candidates;
            this.attempts = attempts;
            this.requestContext = requestContext;
            this.lastError = lastError;
        }

        ChatCompletionRequest request() { return request; }
        ApiKeyEntity apiKey() { return apiKey; }
        String path() { return path; }
        long startedAt() { return startedAt; }
        List<ModelProviderMappingEntity> candidates() { return candidates; }
        List<RoutingAttempt> attempts() { return attempts; }
        RequestContext requestContext() { return requestContext; }
        RuntimeException lastError() { return lastError; }
        void lastError(RuntimeException ex) { this.lastError = ex; }
        RequestExecutionState withContext(RequestContext context) { return new RequestExecutionState(request, apiKey, path, startedAt, candidates, attempts, context, lastError); }
    }
}
