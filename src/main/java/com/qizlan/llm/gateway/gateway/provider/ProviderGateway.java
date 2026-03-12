package com.qizlan.llm.gateway.gateway.provider;

import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import java.util.function.Consumer;

public interface ProviderGateway {

    ProviderChatResult complete(ChatCompletionRequest request, String providerId, String providerModel);

    ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerId, String providerModel);

    ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerId, String providerModel);

    void streamChat(ChatCompletionRequest request, String providerId, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer);
}
