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

    private final java.util.Map<String, BlockingQueue<ProviderChatResult>> completionResponsesByProvider = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, BlockingQueue<UpstreamProviderException>> completionFailuresByProvider = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, BlockingQueue<List<ProviderModelDescriptor>>> modelDescriptorsByProvider = new java.util.concurrent.ConcurrentHashMap<>();

    private String currentProviderId = "mock";
    private final java.util.concurrent.atomic.AtomicReference<String> threadLocalProviderId = new java.util.concurrent.atomic.AtomicReference<>(null);

    public void setCurrentProviderId(String providerId) {
        this.currentProviderId = providerId;
    }

    private String getProviderId() {
        String id = threadLocalProviderId.get();
        return id != null ? id : currentProviderId;
    }

    private BlockingQueue<ProviderChatResult> getResponseQueue(String providerId) {
        return completionResponsesByProvider.computeIfAbsent(providerId, k -> new LinkedBlockingQueue<>());
    }

    private BlockingQueue<UpstreamProviderException> getFailureQueue(String providerId) {
        return completionFailuresByProvider.computeIfAbsent(providerId, k -> new LinkedBlockingQueue<>());
    }

    private BlockingQueue<List<ProviderModelDescriptor>> getModelDescriptorQueue(String providerId) {
        return modelDescriptorsByProvider.computeIfAbsent(providerId, k -> new LinkedBlockingQueue<>());
    }

    public void enqueueCompletionResponse(ProviderChatResult result) {
        enqueueCompletionResponse(currentProviderId, result);
    }

    public void enqueueCompletionResponse(String providerId, ProviderChatResult result) {
        getResponseQueue(providerId).add(result);
    }

    public void enqueueModelList(List<ProviderModelDescriptor> descriptors) {
        enqueueModelList(currentProviderId, descriptors);
    }

    public void enqueueModelList(String providerId, List<ProviderModelDescriptor> descriptors) {
        getModelDescriptorQueue(providerId).add(descriptors);
    }

    public void reset() {
        completionResponsesByProvider.clear();
        completionFailuresByProvider.clear();
        modelDescriptorsByProvider.clear();
    }

    public void enqueueFailure(UpstreamProviderException failure) {
        enqueueFailure(currentProviderId, failure);
    }

    public void enqueueFailure(String providerId, UpstreamProviderException failure) {
        getFailureQueue(providerId).add(failure);
    }

    @Override
    public String providerId() {
        return "mock";
    }

    @Override
    public ProviderChatResult complete(ChatCompletionRequest request, String providerModel) {
        String providerId = getProviderId();
        boolean imageRequest = request.image_config() != null
                || request.messages().stream().anyMatch(msg -> msg.content() instanceof List<?>);
        String promptSummary = request.messages().stream()
                .map(msg -> stringify(msg.content()))
                .filter(value -> !value.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("No prompt");
        UpstreamProviderException failure = getFailureQueue(providerId).poll();
        if (failure != null) {
            throw failure;
        }
        String fallback = imageRequest
                ? "Generated image for prompt: " + promptSummary
                : "Mock response for " + providerModel + ": " + promptSummary;
        return nextResult(providerId, providerModel, fallback, imageRequest);
    }

    @Override
    public Mono<ProviderChatResult> completeAsync(ChatCompletionRequest request, String providerModel) {
        return Mono.deferContextual(contextView -> {
            String providerId = contextView.getOrDefault("PROVIDER_ID", currentProviderId);
            boolean imageRequest = request.image_config() != null
                    || request.messages().stream().anyMatch(msg -> msg.content() instanceof List<?>);
            String promptSummary = request.messages().stream()
                    .map(msg -> stringify(msg.content()))
                    .filter(value -> !value.isBlank())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("No prompt");
            UpstreamProviderException failure = getFailureQueue(providerId).poll();
            if (failure != null) {
                return Mono.error(failure);
            }
            String fallback = imageRequest
                    ? "Generated image for prompt: " + promptSummary
                    : "Mock response for " + providerModel + ": " + promptSummary;
            ProviderChatResult result = nextResult(providerId, providerModel, fallback, imageRequest);
            return Mono.just(result);
        });
    }

    @Override
    public ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerModel) {
        return ImageDtos.ImageResponse.single(request.prompt());
    }

    @Override
    public Mono<ImageDtos.ImageResponse> generateImageAsync(ImageDtos.ImageGenerationRequest request, String providerModel) {
        return Mono.deferContextual(contextView -> {
            String providerId = contextView.getOrDefault("PROVIDER_ID", currentProviderId);
            return Mono.just(generateImage(request, providerModel));
        });
    }

    @Override
    public ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerModel) {
        return ImageDtos.ImageResponse.single(request.prompt());
    }

    @Override
    public Mono<ImageDtos.ImageResponse> editImageAsync(ImageDtos.ImageEditRequest request, String providerModel) {
        return Mono.deferContextual(contextView -> {
            String providerId = contextView.getOrDefault("PROVIDER_ID", currentProviderId);
            return Mono.just(editImage(request, providerModel));
        });
    }

    @Override
    public List<ProviderModelDescriptor> listModels() {
        String providerId = getProviderId();
        List<ProviderModelDescriptor> queued = getModelDescriptorQueue(providerId).poll();
        return queued == null ? List.of() : queued;
    }

    @Override
    public void streamChat(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer) {
        String providerId = getProviderId();
        ProviderChatResult result = nextResult(providerId, providerModel, "mock stream", false);
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
        return Flux.deferContextual(contextView -> {
            String providerId = contextView.getOrDefault("PROVIDER_ID", currentProviderId);
            ProviderChatResult result = nextResult(providerId, providerModel, "mock stream", false);
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
        });
    }

    private ProviderChatResult nextResult(String providerId, String providerModel, String fallback, boolean imageRequest) {
        ProviderChatResult queued = getResponseQueue(providerId).poll();
        if (queued != null) {
            return queued;
        }
        throw UpstreamProviderException.network(providerId, "No mock response queued for provider: " + providerId);
    }
}
