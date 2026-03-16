package com.qizlan.llm.gateway.gateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.gateway.service.RequestContext;
import com.qizlan.llm.gateway.gateway.service.RequestContextService;
import io.micrometer.tracing.Tracer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

abstract class AbstractHttpProviderAdapter implements ProviderAdapter {

    protected final WebClient webClient;
    protected final ObjectMapper objectMapper;
    protected final String baseUrl;
    protected final Tracer tracer;

    protected AbstractHttpProviderAdapter(String baseUrl, ObjectMapper objectMapper, Tracer tracer) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.tracer = tracer;
        this.webClient = WebClient.builder()
                .baseUrl(this.baseUrl)
                .build();
    }

    protected String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    protected List<Map<String, Object>> toMessagePayload(List<com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest.ChatMessageInput> messages) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest.ChatMessageInput message : messages) {
            payload.add(Map.of(
                    "role", message.role(),
                    "content", message.content() == null ? "" : message.content()
            ));
        }
        return payload;
    }

    protected String readText(JsonNode root, String path) {
        JsonNode node = root.at(path);
        return node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    protected Integer readInt(JsonNode root, String path) {
        JsonNode node = root.at(path);
        return node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    protected UpstreamProviderException mapException(String providerId, WebClientResponseException ex) {
        String message = providerId + " upstream error: " + ex.getStatusCode().value();
        return UpstreamProviderException.fromStatus(providerId, ex.getStatusCode().value(), message);
    }

    protected UpstreamProviderException mapRequestException(String providerId, WebClientRequestException ex) {
        String message = providerId + " request error: " + ex.getMessage();
        Throwable cause = ex.getCause();
        if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException || cause instanceof java.util.concurrent.TimeoutException) {
            return UpstreamProviderException.timeout(providerId, message);
        }
        if (message.toLowerCase().contains("timed out")) {
            return UpstreamProviderException.timeout(providerId, message);
        }
        return UpstreamProviderException.network(providerId, message);
    }

    protected JsonNode getJson(String uri, Map<String, String> headers) {
        return getJsonAsync(uri, headers).block();
    }

    protected JsonNode postJson(String uri, Map<String, String> headers, Object body) {
        return postJsonAsync(uri, headers, body).block();
    }

    protected Mono<JsonNode> getJsonAsync(String uri, Map<String, String> headers) {
        return Mono.deferContextual(contextView -> webClient.get()
                .uri(uri)
                .headers(httpHeaders -> {
                    headers.forEach(httpHeaders::add);
                    applyTraceHeaders(httpHeaders, contextView.getOrDefault(RequestContextService.REACTOR_CONTEXT_KEY, null));
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientResponseException.class, ex -> mapException(providerId(), ex))
                .onErrorMap(WebClientRequestException.class, ex -> mapRequestException(providerId(), ex)));
    }

    protected Mono<JsonNode> postJsonAsync(String uri, Map<String, String> headers, Object body) {
        return Mono.deferContextual(contextView -> webClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> {
                    headers.forEach(httpHeaders::add);
                    applyTraceHeaders(httpHeaders, contextView.getOrDefault(RequestContextService.REACTOR_CONTEXT_KEY, null));
                })
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientResponseException.class, ex -> mapException(providerId(), ex))
                .onErrorMap(WebClientRequestException.class, ex -> mapRequestException(providerId(), ex)));
    }

    protected void streamOpenAiSse(String uri, Map<String, String> headers, Object body, Consumer<ProviderStreamEvent> consumer, String providerId) {
        streamSse(uri, headers, body, providerId, false, consumer);
    }

    protected void streamAnthropicSse(String uri, Map<String, String> headers, Object body, Consumer<ProviderStreamEvent> consumer, String providerId) {
        streamSse(uri, headers, body, providerId, true, consumer);
    }

    private void streamSse(String uri, Map<String, String> headers, Object body, String providerId, boolean anthropicFormat, Consumer<ProviderStreamEvent> consumer) {
        streamSseAsync(uri, headers, body, providerId, anthropicFormat)
                .doOnNext(consumer)
                .blockLast();
    }

    protected Flux<ProviderStreamEvent> streamOpenAiSseAsync(String uri, Map<String, String> headers, Object body, String providerId) {
        return streamSseAsync(uri, headers, body, providerId, false);
    }

    protected Flux<ProviderStreamEvent> streamAnthropicSseAsync(String uri, Map<String, String> headers, Object body, String providerId) {
        return streamSseAsync(uri, headers, body, providerId, true);
    }

    private Flux<ProviderStreamEvent> streamSseAsync(String uri, Map<String, String> headers, Object body, String providerId, boolean anthropicFormat) {
        try {
            return Flux.deferContextual(contextView -> webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .headers(httpHeaders -> {
                        headers.forEach(httpHeaders::add);
                        applyTraceHeaders(httpHeaders, contextView.getOrDefault(RequestContextService.REACTOR_CONTEXT_KEY, null));
                    })
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .flatMapIterable(chunk -> anthropicFormat ? emitAnthropicEvents(chunk) : emitOpenAiEvents(chunk))
                    .onErrorMap(WebClientResponseException.class, ex -> mapException(providerId, ex))
                    .onErrorMap(WebClientRequestException.class, ex -> mapRequestException(providerId, ex))
                    .onErrorMap(RuntimeException.class, ex -> ex instanceof UpstreamProviderException ? ex : UpstreamProviderException.network(providerId, providerId + " stream error")));
        } catch (RuntimeException ex) {
            return Flux.error(ex instanceof UpstreamProviderException ? ex : UpstreamProviderException.network(providerId, providerId + " stream error"));
        }
    }

    private void applyTraceHeaders(org.springframework.http.HttpHeaders headers, RequestContext requestContext) {
        if (requestContext != null && requestContext.correlationId() != null && !requestContext.correlationId().isBlank()) {
            headers.set("X-Correlation-Id", requestContext.correlationId());
            headers.set("X-Trace-Id", requestContext.traceId());
            headers.set("X-Span-Id", requestContext.spanId());
        }
        if (tracer.currentSpan() != null) {
            headers.set("X-Trace-Id", tracer.currentSpan().context().traceId());
            headers.set("X-Span-Id", tracer.currentSpan().context().spanId());
        }
    }

    private List<ProviderStreamEvent> emitOpenAiEvents(String chunk) {
        List<ProviderStreamEvent> events = new ArrayList<>();
        if (chunk == null || chunk.isBlank()) {
            return events;
        }
        for (String line : chunk.split("\\r?\\n")) {
            String data;
            if (line.startsWith("data:")) {
                data = line.substring("data:".length()).trim();
            } else if (line.startsWith("event:") || line.isBlank()) {
                continue;
            } else {
                data = line.trim();
            }
            events.add(new ProviderStreamEvent(null, data, "[DONE]".equals(data)));
        }
        return events;
    }

    private List<ProviderStreamEvent> emitAnthropicEvents(String chunk) {
        List<ProviderStreamEvent> events = new ArrayList<>();
        String eventName = null;
        String data = null;
        if (chunk == null) {
            return events;
        }
        for (String line : chunk.split("\\r?\\n")) {
            if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                data = line.substring("data:".length()).trim();
            } else if (!line.isBlank()) {
                data = line.trim();
            } else if (data != null) {
                events.add(new ProviderStreamEvent(eventName, data, "message_stop".equals(eventName)));
                eventName = null;
                data = null;
            }
        }
        if (data != null) {
            events.add(new ProviderStreamEvent(eventName, data, "message_stop".equals(eventName)));
        }
        return events;
    }
}
