package com.qizlan.llm.gateway.gateway.provider;

import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import java.util.List;
import java.util.function.Consumer;

public interface ProviderAdapter {

    String providerId();

    ProviderChatResult complete(ChatCompletionRequest request, String providerModel);

    ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerModel);

    ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerModel);

    List<ProviderModelDescriptor> listModels();

    void streamChat(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer);
}
