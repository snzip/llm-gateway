package com.qizlan.llm.gateway.gateway.provider;

import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProviderGateway {

    ProviderChatResult complete(ChatCompletionRequest request, String providerId, String providerModel);

    Mono<ProviderChatResult> completeAsync(ChatCompletionRequest request, String providerId, String providerModel);

    ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerId, String providerModel);

    Mono<ImageDtos.ImageResponse> generateImageAsync(ImageDtos.ImageGenerationRequest request, String providerId, String providerModel);

    ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerId, String providerModel);

    Mono<ImageDtos.ImageResponse> editImageAsync(ImageDtos.ImageEditRequest request, String providerId, String providerModel);

    void streamChat(ChatCompletionRequest request, String providerId, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer);

    Flux<ProviderStreamEvent> streamChatAsync(ChatCompletionRequest request, String providerId, String providerModel, ProviderStreamFormat format);
}
