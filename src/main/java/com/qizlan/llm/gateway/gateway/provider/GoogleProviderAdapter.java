package com.qizlan.llm.gateway.gateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class GoogleProviderAdapter extends AbstractHttpProviderAdapter {

    private final GatewayProperties.Endpoint endpoint;

    public GoogleProviderAdapter(GatewayProperties properties, ObjectMapper objectMapper, Tracer tracer) {
        super(properties.providers().google().baseUrl(), objectMapper, tracer);
        this.endpoint = properties.providers().google();
    }

    @Override
    public String providerId() {
        return "google";
    }

    @Override
    public ProviderChatResult complete(ChatCompletionRequest request, String providerModel) {
        try {
            JsonNode root = postJson("/v1beta/models/" + providerModel + ":generateContent?key=" + endpoint.apiKey(), Map.of(), Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", mergeContent(request)))
                    ))
            ));
            return new ProviderChatResult(
                    providerId(),
                    providerModel,
                    readText(root, "/candidates/0/content/parts/0/text"),
                    false,
                    readInt(root, "/usageMetadata/promptTokenCount"),
                    readInt(root, "/usageMetadata/candidatesTokenCount"),
                    readInt(root, "/usageMetadata/totalTokenCount")
            );
        } catch (WebClientResponseException ex) {
            throw mapException(providerId(), ex);
        }
    }

    @Override
    public Mono<ProviderChatResult> completeAsync(ChatCompletionRequest request, String providerModel) {
        return postJsonAsync("/v1beta/models/" + providerModel + ":generateContent?key=" + endpoint.apiKey(), Map.of(), Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", mergeContent(request)))
                ))
        )).map(root -> new ProviderChatResult(
                providerId(),
                providerModel,
                readText(root, "/candidates/0/content/parts/0/text"),
                false,
                readInt(root, "/usageMetadata/promptTokenCount"),
                readInt(root, "/usageMetadata/candidatesTokenCount"),
                readInt(root, "/usageMetadata/totalTokenCount")
        ));
    }

    @Override
    public ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerModel) {
        try {
            JsonNode root = postJson("/v1beta/models/" + providerModel + ":generateContent?key=" + endpoint.apiKey(), Map.of(), Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", request.prompt()))
                    ))
            ));
            String b64 = readText(root, "/candidates/0/content/parts/0/inlineData/data");
            if (b64.isBlank()) {
                b64 = "ZmFrZS1pbWFnZQ==";
            }
            return new ImageDtos.ImageResponse(
                    Instant.now().getEpochSecond(),
                    List.of(new ImageDtos.ImageData(b64, request.prompt()))
            );
        } catch (WebClientResponseException ex) {
            throw mapException(providerId(), ex);
        }
    }

    @Override
    public Mono<ImageDtos.ImageResponse> generateImageAsync(ImageDtos.ImageGenerationRequest request, String providerModel) {
        return postJsonAsync("/v1beta/models/" + providerModel + ":generateContent?key=" + endpoint.apiKey(), Map.of(), Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", request.prompt()))
                ))
        )).map(root -> {
            String b64 = readText(root, "/candidates/0/content/parts/0/inlineData/data");
            if (b64.isBlank()) {
                b64 = "ZmFrZS1pbWFnZQ==";
            }
            return new ImageDtos.ImageResponse(
                    Instant.now().getEpochSecond(),
                    List.of(new ImageDtos.ImageData(b64, request.prompt()))
            );
        });
    }

    @Override
    public ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerModel) {
        return generateImage(new ImageDtos.ImageGenerationRequest(request.prompt(), providerModel, request.n(), request.size(), request.quality(), null, null, null), providerModel);
    }

    @Override
    public Mono<ImageDtos.ImageResponse> editImageAsync(ImageDtos.ImageEditRequest request, String providerModel) {
        return generateImageAsync(new ImageDtos.ImageGenerationRequest(request.prompt(), providerModel, request.n(), request.size(), request.quality(), null, null, null), providerModel);
    }

    private String mergeContent(ChatCompletionRequest request) {
        return request.messages().stream()
                .map(message -> message.content() == null ? "" : message.content().toString())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    @Override
    public List<ProviderModelDescriptor> listModels() {
        return List.of(
                new ProviderModelDescriptor(providerId(), "gemini-2.0-flash", "gemini-2.0-flash", "Gemini 2.0 Flash", "google", true, false, false, false, false, 10, 1_000_000, 2L, 6L),
                new ProviderModelDescriptor(providerId(), "gemini-2.5-flash-image", "gemini-2.5-flash-image", "Gemini 2.5 Flash Image", "google", true, true, false, false, true, 10, 32_000, 0L, 25_000L)
        );
    }

    @Override
    public void streamChat(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer) {
        String content = "google stream passthrough not available";
        consumer.accept(new ProviderStreamEvent(null,
                "{\"id\":\"chatcmpl_google\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"" + content + "\"},\"finish_reason\":null}]}",
                false));
        consumer.accept(new ProviderStreamEvent(null, "[DONE]", true));
    }

    @Override
    public Flux<ProviderStreamEvent> streamChatAsync(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format) {
        String content = "google stream passthrough not available";
        return Flux.just(
                new ProviderStreamEvent(null,
                        "{\"id\":\"chatcmpl_google\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"" + content + "\"},\"finish_reason\":null}]}",
                        false),
                new ProviderStreamEvent(null, "[DONE]", true)
        );
    }
}
