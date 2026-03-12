package com.qizlan.llm.gateway.gateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

abstract class AbstractHttpProviderAdapter implements ProviderAdapter {

    protected final RestClient restClient;
    protected final ObjectMapper objectMapper;
    protected final HttpClient httpClient;
    protected final String baseUrl;

    protected AbstractHttpProviderAdapter(String baseUrl, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .build();
        this.httpClient = HttpClient.newHttpClient();
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

    protected RuntimeException mapException(String providerId, RestClientResponseException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        String message = providerId + " upstream error: " + statusCode.value();
        return new UpstreamProviderException(providerId, statusCode.value(), message);
    }

    protected HttpRequest buildJsonRequest(String uri, Map<String, String> headers, ObjectNode body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + uri))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        headers.forEach(builder::header);
        return builder.build();
    }

    protected void streamOpenAiSse(HttpRequest request, java.util.function.Consumer<ProviderStreamEvent> consumer, String providerId) {
        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new UpstreamProviderException(providerId, response.statusCode(), providerId + " upstream error: " + response.statusCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).trim();
                    consumer.accept(new ProviderStreamEvent(null, data, "[DONE]".equals(data)));
                }
            }
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(providerId + " stream error");
        }
    }

    protected void streamAnthropicSse(HttpRequest request, java.util.function.Consumer<ProviderStreamEvent> consumer, String providerId) {
        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new UpstreamProviderException(providerId, response.statusCode(), providerId + " upstream error: " + response.statusCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String eventName = null;
                String data = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        eventName = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        data = line.substring("data:".length()).trim();
                    } else if (line.isBlank() && data != null) {
                        consumer.accept(new ProviderStreamEvent(eventName, data, "message_stop".equals(eventName)));
                        eventName = null;
                        data = null;
                    }
                }
            }
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(providerId + " stream error");
        }
    }
}
