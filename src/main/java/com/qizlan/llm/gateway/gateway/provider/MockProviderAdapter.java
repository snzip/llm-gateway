package com.qizlan.llm.gateway.gateway.provider;

import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import com.qizlan.llm.gateway.gateway.provider.ProviderModelDescriptor;
import com.qizlan.llm.gateway.gateway.provider.UpstreamProviderException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class MockProviderAdapter implements ProviderAdapter {

    private final BlockingQueue<ProviderChatResult> completionResponses = new LinkedBlockingQueue<>();
    private final BlockingQueue<UpstreamProviderException> completionFailures = new LinkedBlockingQueue<>();
    private final BlockingQueue<List<ProviderModelDescriptor>> modelDescriptors = new LinkedBlockingQueue<>();

    public void enqueueCompletionResponse(ProviderChatResult result) {
        completionResponses.add(result);
    }

    public void enqueueModelList(List<ProviderModelDescriptor> descriptors) {
        modelDescriptors.add(descriptors);
    }

    public void reset() {
        completionResponses.clear();
        completionFailures.clear();
        modelDescriptors.clear();
    }

    public void enqueueFailure(UpstreamProviderException failure) {
        completionFailures.add(failure);
    }

    @Override
    public String providerId() {
        return "mock";
    }

    @Override
    public ProviderChatResult complete(ChatCompletionRequest request, String providerModel) {
        boolean imageRequest = request.image_config() != null
                || request.messages().stream().anyMatch(msg -> msg.content() instanceof List<?>);
        String promptSummary = request.messages().stream()
                .map(msg -> stringify(msg.content()))
                .filter(value -> !value.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("No prompt");
        UpstreamProviderException failure = completionFailures.poll();
        if (failure != null) {
            throw failure;
        }
        String fallback = imageRequest
                ? "Generated image for prompt: " + promptSummary
                : "Mock response for " + providerModel + ": " + promptSummary;
        return nextResult(providerModel, fallback, imageRequest);
    }

    @Override
    public Mono<ProviderChatResult> completeAsync(ChatCompletionRequest request, String providerModel) {
        return Mono.just(complete(request, providerModel));
    }

    @Override
    public ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerModel) {
        return ImageDtos.ImageResponse.single(request.prompt());
    }

    @Override
    public Mono<ImageDtos.ImageResponse> generateImageAsync(ImageDtos.ImageGenerationRequest request, String providerModel) {
        return Mono.just(generateImage(request, providerModel));
    }

    @Override
    public ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerModel) {
        return ImageDtos.ImageResponse.single(request.prompt());
    }

    @Override
    public Mono<ImageDtos.ImageResponse> editImageAsync(ImageDtos.ImageEditRequest request, String providerModel) {
        return Mono.just(editImage(request, providerModel));
    }

    @Override
    public List<ProviderModelDescriptor> listModels() {
        List<ProviderModelDescriptor> queued = modelDescriptors.poll();
        return queued == null ? List.of() : queued;
    }

    @Override
    public void streamChat(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer) {
        ProviderChatResult result = nextResult(providerModel, "mock stream", false);
        consumer.accept(new ProviderStreamEvent(
                format == ProviderStreamFormat.ANTHROPIC ? "content_block_delta" : null,
                format == ProviderStreamFormat.ANTHROPIC
                        ? "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"" + result.content() + "\"}}"
                        : "{\"id\":\"chatcmpl_mock\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"" + result.content() + "\"},\"finish_reason\":null}]}",
                false
        ));
        consumer.accept(new ProviderStreamEvent(format == ProviderStreamFormat.ANTHROPIC ? "message_stop" : null, format == ProviderStreamFormat.ANTHROPIC ? "{\"type\":\"message_stop\"}" : "[DONE]", true));
    }

    private String stringify(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String value) {
            return value;
        }
        if (content instanceof List<?> list) {
            return list.stream()
                    .map(item -> {
                        if (item instanceof Map<?, ?> map && "text".equals(map.get("type"))) {
                            Object text = map.get("text");
                            return text == null ? "" : text.toString();
                        }
                        return "";
                    })
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
        }
        return content.toString();
    }

    @Override
    public Flux<ProviderStreamEvent> streamChatAsync(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format) {
        ProviderChatResult result = nextResult(providerModel, "mock stream", false);
        return Flux.just(
                new ProviderStreamEvent(
                        format == ProviderStreamFormat.ANTHROPIC ? "content_block_delta" : null,
                        format == ProviderStreamFormat.ANTHROPIC
                                ? "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"" + result.content() + "\"}}"
                                : "{\"id\":\"chatcmpl_mock\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"" + result.content() + "\"},\"finish_reason\":null}]}",
                        false
                ),
                new ProviderStreamEvent(format == ProviderStreamFormat.ANTHROPIC ? "message_stop" : null, format == ProviderStreamFormat.ANTHROPIC ? "{\"type\":\"message_stop\"}" : "[DONE]", true)
        );
    }

    private ProviderChatResult nextResult(String providerModel, String fallback, boolean imageRequest) {
        ProviderChatResult queued = completionResponses.poll();
        if (queued != null) {
            return queued;
        }
        return new ProviderChatResult("mock", providerModel, fallback, imageRequest, 12, 24, 36);
    }
}
