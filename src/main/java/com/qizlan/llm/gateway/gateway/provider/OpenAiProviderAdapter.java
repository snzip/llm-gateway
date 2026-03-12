package com.qizlan.llm.gateway.gateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class OpenAiProviderAdapter extends AbstractHttpProviderAdapter {

    private final GatewayProperties.Endpoint endpoint;

    public OpenAiProviderAdapter(GatewayProperties properties, ObjectMapper objectMapper) {
        super(properties.providers().openai().baseUrl(), objectMapper);
        this.endpoint = properties.providers().openai();
    }

    @Override
    public String providerId() {
        return "openai";
    }

    @Override
    public ProviderChatResult complete(ChatCompletionRequest request, String providerModel) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", providerModel);
            body.put("messages", toMessagePayload(request.messages()));
            body.put("stream", false);
            if (request.temperature() != null) {
                body.put("temperature", request.temperature());
            }
            if (request.max_tokens() != null) {
                body.put("max_tokens", request.max_tokens());
            }
            JsonNode root = postJson("/v1/chat/completions", Map.of("Authorization", "Bearer " + endpoint.apiKey()), body);
            String content = readText(root, "/choices/0/message/content");
            return new ProviderChatResult(
                    providerId(),
                    providerModel,
                    content,
                    false,
                    readInt(root, "/usage/prompt_tokens"),
                    readInt(root, "/usage/completion_tokens"),
                    readInt(root, "/usage/total_tokens")
            );
        } catch (WebClientResponseException ex) {
            throw mapException(providerId(), ex);
        }
    }

    @Override
    public ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerModel) {
        try {
            JsonNode root = postJson("/v1/images/generations", Map.of("Authorization", "Bearer " + endpoint.apiKey()), Map.of(
                    "model", providerModel,
                    "prompt", request.prompt(),
                    "n", request.n() == null ? 1 : request.n()
            ));
            return mapImageResponse(root, request.prompt());
        } catch (WebClientResponseException ex) {
            throw mapException(providerId(), ex);
        }
    }

    @Override
    public ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerModel) {
        try {
            JsonNode root = postJson("/v1/images/edits", Map.of("Authorization", "Bearer " + endpoint.apiKey()), Map.of(
                    "model", providerModel,
                    "prompt", request.prompt(),
                    "n", request.n() == null ? 1 : request.n()
            ));
            return mapImageResponse(root, request.prompt());
        } catch (WebClientResponseException ex) {
            throw mapException(providerId(), ex);
        }
    }

    private ImageDtos.ImageResponse mapImageResponse(JsonNode root, String fallbackPrompt) {
        JsonNode data = root.path("data");
        String b64 = data.isArray() && !data.isEmpty() ? data.get(0).path("b64_json").asText("ZmFrZS1pbWFnZQ==") : "ZmFrZS1pbWFnZQ==";
        String revisedPrompt = data.isArray() && !data.isEmpty() ? data.get(0).path("revised_prompt").asText(fallbackPrompt) : fallbackPrompt;
        return new ImageDtos.ImageResponse(
                root.path("created").asLong(java.time.Instant.now().getEpochSecond()),
                List.of(new ImageDtos.ImageData(b64, revisedPrompt))
        );
    }

    @Override
    public List<ProviderModelDescriptor> listModels() {
        try {
            JsonNode root = getJson("/v1/models", Map.of("Authorization", "Bearer " + endpoint.apiKey()));
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }
            return java.util.stream.StreamSupport.stream(data.spliterator(), false)
                    .map(node -> {
                        String id = node.path("id").asText();
                        boolean image = id.contains("image");
                        return new ProviderModelDescriptor(
                                providerId(),
                                id,
                                id,
                                id,
                                providerId(),
                                true,
                                id.contains("vision") || id.contains("4o"),
                                !image,
                                !image,
                                image,
                                10,
                                inferContextWindow(id),
                                inferInputCost(id, image),
                                inferOutputCost(id, image)
                        );
                    })
                    .toList();
        } catch (WebClientResponseException ex) {
            throw mapException(providerId(), ex);
        }
    }

    @Override
    public void streamChat(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", providerModel);
        body.put("messages", toMessagePayload(request.messages()));
        body.put("stream", true);
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if (request.max_tokens() != null) {
            body.put("max_tokens", request.max_tokens());
        }
        streamOpenAiSse("/v1/chat/completions", Map.of("Authorization", "Bearer " + endpoint.apiKey()), body, consumer, providerId());
    }

    private int inferContextWindow(String id) {
        if (id.contains("4.1")) {
            return 1_000_000;
        }
        if (id.contains("4o")) {
            return 128_000;
        }
        if (id.contains("image")) {
            return 32_000;
        }
        return 128_000;
    }

    private long inferInputCost(String id, boolean image) {
        if (image) {
            return 0L;
        }
        if (id.contains("4.1")) {
            return 4L;
        }
        if (id.contains("4o")) {
            return 5L;
        }
        return 5L;
    }

    private long inferOutputCost(String id, boolean image) {
        if (image) {
            return 25_000L;
        }
        if (id.contains("4.1")) {
            return 12L;
        }
        if (id.contains("4o")) {
            return 15L;
        }
        return 15L;
    }
}
