package com.qizlan.llm.gateway.gateway.provider;

import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class RoutingProviderGateway implements ProviderGateway {

    private final Map<String, ProviderAdapter> adapters;
    private final MockProviderAdapter mockProviderAdapter;
    private final GatewayProperties properties;

    public RoutingProviderGateway(List<ProviderAdapter> adapters, MockProviderAdapter mockProviderAdapter, GatewayProperties properties) {
        this.adapters = adapters.stream().collect(java.util.stream.Collectors.toMap(ProviderAdapter::providerId, adapter -> adapter));
        this.mockProviderAdapter = mockProviderAdapter;
        this.properties = properties;
    }

    @Override
    public ProviderChatResult complete(ChatCompletionRequest request, String providerId, String providerModel) {
        return resolve(providerId, () -> select(providerId).complete(request, providerModel), () -> {
            mockProviderAdapter.setCurrentProviderId(providerId);
            return mockProviderAdapter.complete(request, providerModel);
        });
    }

    @Override
    public Mono<ProviderChatResult> completeAsync(ChatCompletionRequest request, String providerId, String providerModel) {
        if ("mock".equalsIgnoreCase(properties.providers().mode())) {
            return mockProviderAdapter.completeAsync(request, providerModel)
                    .contextWrite(ctx -> ctx.put("PROVIDER_ID", providerId));
        }
        return isEnabled(providerId) ? select(providerId).completeAsync(request, providerModel) : mockProviderAdapter.completeAsync(request, providerModel);
    }

    @Override
    public ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerId, String providerModel) {
        return resolve(providerId, () -> select(providerId).generateImage(request, providerModel), () -> {
            mockProviderAdapter.setCurrentProviderId(providerId);
            return mockProviderAdapter.generateImage(request, providerModel);
        });
    }

    @Override
    public Mono<ImageDtos.ImageResponse> generateImageAsync(ImageDtos.ImageGenerationRequest request, String providerId, String providerModel) {
        if ("mock".equalsIgnoreCase(properties.providers().mode())) {
            return mockProviderAdapter.generateImageAsync(request, providerModel)
                    .contextWrite(ctx -> ctx.put("PROVIDER_ID", providerId));
        }
        return isEnabled(providerId) ? select(providerId).generateImageAsync(request, providerModel) : mockProviderAdapter.generateImageAsync(request, providerModel);
    }

    @Override
    public ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerId, String providerModel) {
        return resolve(providerId, () -> select(providerId).editImage(request, providerModel), () -> {
            mockProviderAdapter.setCurrentProviderId(providerId);
            return mockProviderAdapter.editImage(request, providerModel);
        });
    }

    @Override
    public Mono<ImageDtos.ImageResponse> editImageAsync(ImageDtos.ImageEditRequest request, String providerId, String providerModel) {
        if ("mock".equalsIgnoreCase(properties.providers().mode())) {
            return mockProviderAdapter.editImageAsync(request, providerModel)
                    .contextWrite(ctx -> ctx.put("PROVIDER_ID", providerId));
        }
        return isEnabled(providerId) ? select(providerId).editImageAsync(request, providerModel) : mockProviderAdapter.editImageAsync(request, providerModel);
    }

    @Override
    public void streamChat(ChatCompletionRequest request, String providerId, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer) {
        resolve(providerId, () -> {
            select(providerId).streamChat(request, providerModel, format, consumer);
            return null;
        }, () -> {
            mockProviderAdapter.setCurrentProviderId(providerId);
            mockProviderAdapter.streamChat(request, providerModel, format, consumer);
            return null;
        });
    }

    @Override
    public Flux<ProviderStreamEvent> streamChatAsync(ChatCompletionRequest request, String providerId, String providerModel, ProviderStreamFormat format) {
        if ("mock".equalsIgnoreCase(properties.providers().mode())) {
            return mockProviderAdapter.streamChatAsync(request, providerModel, format)
                    .contextWrite(ctx -> ctx.put("PROVIDER_ID", providerId));
        }
        return isEnabled(providerId) ? select(providerId).streamChatAsync(request, providerModel, format) : mockProviderAdapter.streamChatAsync(request, providerModel, format);
    }

    private <T> T resolve(String providerId, Supplier<T> realCall, Supplier<T> mockCall) {
        if ("mock".equalsIgnoreCase(properties.providers().mode())) {
            return mockCall.get();
        }
        return isEnabled(providerId) ? realCall.get() : mockCall.get();
    }

    private ProviderAdapter select(String providerId) {
        ProviderAdapter adapter = adapters.get(providerId);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported provider: " + providerId);
        }
        return adapter;
    }

    private boolean isEnabled(String providerId) {
        return switch (providerId) {
            case "openai" -> properties.providers().openai().enabled();
            case "anthropic" -> properties.providers().anthropic().enabled();
            case "google" -> properties.providers().google().enabled();
            default -> false;
        };
    }
}
