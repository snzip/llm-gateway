package com.qizlan.llm.gateway.gateway.provider;

import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class MockProviderAdapter implements ProviderAdapter {

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
        String content = imageRequest
                ? "Generated image for prompt: " + promptSummary
                : "Mock response for " + providerModel + ": " + promptSummary;
        return new ProviderChatResult("mock", providerModel, content, imageRequest, 12, 24, 36);
    }

    @Override
    public ImageDtos.ImageResponse generateImage(ImageDtos.ImageGenerationRequest request, String providerModel) {
        return ImageDtos.ImageResponse.single(request.prompt());
    }

    @Override
    public ImageDtos.ImageResponse editImage(ImageDtos.ImageEditRequest request, String providerModel) {
        return ImageDtos.ImageResponse.single(request.prompt());
    }

    @Override
    public List<ProviderModelDescriptor> listModels() {
        return List.of();
    }

    @Override
    public void streamChat(ChatCompletionRequest request, String providerModel, ProviderStreamFormat format, Consumer<ProviderStreamEvent> consumer) {
        consumer.accept(new ProviderStreamEvent(
                format == ProviderStreamFormat.ANTHROPIC ? "content_block_delta" : null,
                format == ProviderStreamFormat.ANTHROPIC
                        ? "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"mock stream\"}}"
                        : "{\"id\":\"chatcmpl_mock\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"mock stream\"},\"finish_reason\":null}]}",
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
}
