package com.qizlan.llm.gateway.gateway.provider;

import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import java.util.List;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProviderAdapter {

    String providerId();

    ProviderChatResult complete(ChatCompletionRequest request, String providerModel);

    default Mono<ProviderChatResult> completeAsync(ChatCompletionRequest request, String providerModel) {
        return Mono.fromCallable(() -> complete(request, providerModel));
    }

    ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerModel);

    default Mono<ImageDtos.ImageResponse> generateImageAsync(ImageDtos.ImageGenerationRequest request, String providerModel) {
        return Mono.fromCallable(() -> generateImage(request, providerModel));
    }

    ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerModel);

    default Mono<ImageDtos.ImageResponse> editImageAsync(ImageDtos.ImageEditRequest request, String providerModel) {
        return Mono.fromCallable(() -> editImage(request, providerModel));
    }

    List<ProviderModelDescriptor> listModels();

    void streamChat(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer);

    default Flux<ProviderStreamEvent> streamChatAsync(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format) {
        return Flux.create(sink -> {
            try {
                streamChat(request, providerModel, format, event -> {
                    sink.next(event);
                    if (event.done()) {
                        sink.complete();
                    }
                });
            } catch (RuntimeException ex) {
                sink.error(ex);
            }
        });
    }
}
