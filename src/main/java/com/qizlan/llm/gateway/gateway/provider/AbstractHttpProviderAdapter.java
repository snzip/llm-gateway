package com.qizlan.llm.gateway.gateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

abstract class AbstractHttpProviderAdapter implements ProviderAdapter {

    protected final WebClient webClient;
    protected final ObjectMapper objectMapper;
    protected final String baseUrl;

    protected AbstractHttpProviderAdapter(String baseUrl, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
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
        return webClient.get()
                .uri(uri)
                .headers(httpHeaders -> headers.forEach(httpHeaders::add))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    protected JsonNode postJson(String uri, Map<String, String> headers, Object body) {
        return webClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> headers.forEach(httpHeaders::add))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    protected void streamOpenAiSse(String uri, Map<String, String> headers, Object body, Consumer<ProviderStreamEvent> consumer, String providerId) {
        streamSse(uri, headers, body, providerId, false, consumer);
    }

    protected void streamAnthropicSse(String uri, Map<String, String> headers, Object body, Consumer<ProviderStreamEvent> consumer, String providerId) {
        streamSse(uri, headers, body, providerId, true, consumer);
    }

    private void streamSse(String uri, Map<String, String> headers, Object body, String providerId, boolean anthropicFormat, Consumer<ProviderStreamEvent> consumer) {
        try {
            List<String> lines = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::add))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .collectList()
                    .block();
            if (lines == null) {
                return;
            }
            if (anthropicFormat) {
                emitAnthropicEvents(lines, consumer);
            } else {
                emitOpenAiEvents(lines, consumer);
            }
        } catch (WebClientResponseException ex) {
            throw mapException(providerId, ex);
        } catch (WebClientRequestException ex) {
            throw mapRequestException(providerId, ex);
        } catch (RuntimeException ex) {
            throw UpstreamProviderException.network(providerId, providerId + " stream error");
        }
    }

    private void emitOpenAiEvents(List<String> lines, Consumer<ProviderStreamEvent> consumer) {
        for (String chunk : lines) {
            if (chunk == null || chunk.isBlank()) {
                continue;
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
                consumer.accept(new ProviderStreamEvent(null, data, "[DONE]".equals(data)));
            }
        }
    }

    private void emitAnthropicEvents(List<String> lines, Consumer<ProviderStreamEvent> consumer) {
        String eventName = null;
        String data = null;
        for (String chunk : lines) {
            if (chunk == null) {
                continue;
            }
            for (String line : chunk.split("\\r?\\n")) {
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    data = line.substring("data:".length()).trim();
                } else if (!line.isBlank()) {
                    data = line.trim();
                } else if (line.isBlank() && data != null) {
                    consumer.accept(new ProviderStreamEvent(eventName, data, "message_stop".equals(eventName)));
                    eventName = null;
                    data = null;
                }
            }
        }
        if (data != null) {
            consumer.accept(new ProviderStreamEvent(eventName, data, "message_stop".equals(eventName)));
        }
    }
}
