package com.qizlan.llm.gateway.gateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AnthropicProviderAdapter extends AbstractHttpProviderAdapter {

    private final GatewayProperties.Endpoint endpoint;

    public AnthropicProviderAdapter(GatewayProperties properties, ObjectMapper objectMapper) {
        super(properties.providers().anthropic().baseUrl(), objectMapper);
        this.endpoint = properties.providers().anthropic();
    }

    @Override
    public String providerId() {
        return "anthropic";
    }

    @Override
    public ProviderChatResult complete(ChatCompletionRequest request, String providerModel) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", providerModel);
            body.put("messages", toMessagePayload(request.messages()));
            body.put("max_tokens", request.max_tokens() == null ? 512 : request.max_tokens());
            body.put("stream", false);
            if (request.temperature() != null) {
                body.put("temperature", request.temperature());
            }
            JsonNode root = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", endpoint.apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return new ProviderChatResult(
                    providerId(),
                    providerModel,
                    readText(root, "/content/0/text"),
                    false,
                    readInt(root, "/usage/input_tokens"),
                    readInt(root, "/usage/output_tokens"),
                    sum(readInt(root, "/usage/input_tokens"), readInt(root, "/usage/output_tokens"))
            );
        } catch (RestClientResponseException ex) {
            throw mapException(providerId(), ex);
        }
    }

    @Override
    public ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerModel) {
        throw new IllegalArgumentException("anthropic does not support image generation in this gateway");
    }

    @Override
    public ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerModel) {
        throw new IllegalArgumentException("anthropic does not support image editing in this gateway");
    }

    private Integer sum(Integer a, Integer b) {
        if (a == null && b == null) {
            return null;
        }
        return (a == null ? 0 : a) + (b == null ? 0 : b);
    }

    @Override
    public List<ProviderModelDescriptor> listModels() {
        return List.of(
                new ProviderModelDescriptor(providerId(), "claude-3-5-sonnet", "claude-3-5-sonnet", "Claude 3.5 Sonnet", "anthropic", true, false, true, true, false, 10, 200_000, 6L, 18L)
        );
    }

    @Override
    public void streamChat(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", providerModel);
        body.set("messages", objectMapper.valueToTree(toMessagePayload(request.messages())));
        body.put("max_tokens", request.max_tokens() == null ? 512 : request.max_tokens());
        body.put("stream", true);
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        streamAnthropicSse(buildJsonRequest(
                "/v1/messages",
                Map.of("x-api-key", endpoint.apiKey(), "anthropic-version", "2023-06-01"),
                body), consumer, providerId());
    }
}
