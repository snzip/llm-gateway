package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.dto.AnthropicDtos;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionResponse;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import com.qizlan.llm.gateway.gateway.provider.ProviderStreamFormat;
import com.qizlan.llm.gateway.gateway.service.ChatGatewayService;
import com.qizlan.llm.gateway.gateway.service.ImageGatewayService;
import com.qizlan.llm.gateway.gateway.service.ModelCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1")
public class V1Controller {

    private final ChatGatewayService chatGatewayService;
    private final ModelCatalogService modelCatalogService;
    private final ImageGatewayService imageGatewayService;

    public V1Controller(ChatGatewayService chatGatewayService, ModelCatalogService modelCatalogService, ImageGatewayService imageGatewayService) {
        this.chatGatewayService = chatGatewayService;
        this.modelCatalogService = modelCatalogService;
        this.imageGatewayService = imageGatewayService;
    }

    @GetMapping("/models")
    @Operation(summary = "List all available models")
    public Map<String, Object> models() {
        return Map.of("data", modelCatalogService.listModels());
    }

    @PostMapping(value = "/chat/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    @Operation(summary = "Create a completion for the chat conversation")
    public Object chat(@Valid @RequestBody ChatCompletionRequest request, HttpServletRequest servletRequest) {
        if (!request.streamEnabled()) {
            return chatGatewayService.complete(request, (com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity) servletRequest.getAttribute("apiKey"));
        }
        SseEmitter emitter = new SseEmitter(10_000L);
        new Thread(() -> relayStream(request, servletRequest, emitter, ProviderStreamFormat.OPENAI)).start();
        return emitter;
    }

    @PostMapping("/images/generations")
    @Operation(summary = "Create image")
    public ImageDtos.ImageResponse imageGeneration(@Valid @RequestBody ImageDtos.ImageGenerationRequest request, HttpServletRequest servletRequest) {
        return imageGatewayService.generate(request, (com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity) servletRequest.getAttribute("apiKey"));
    }

    @PostMapping(value = "/images/edits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Edit image")
    public ImageDtos.ImageResponse imageEditJson(@Valid @RequestBody ImageDtos.ImageEditRequest request, HttpServletRequest servletRequest) {
        return imageGatewayService.edit(request, (com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity) servletRequest.getAttribute("apiKey"));
    }

    @PostMapping(value = "/images/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageDtos.ImageResponse imageEditMultipart(
            @RequestPart("prompt") String prompt,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "image[]", required = false) MultipartFile imageArray,
            @RequestPart(value = "file", required = false) MultipartFile file,
            HttpServletRequest servletRequest
    ) {
        MultipartFile selected = image != null ? image : imageArray != null ? imageArray : file;
        if (!StringUtils.hasText(prompt) || selected == null || selected.isEmpty()) {
            throw new IllegalArgumentException("prompt and image file are required");
        }
        return imageGatewayService.edit(
                new ImageDtos.ImageEditRequest(List.of(new ImageDtos.ImageEditRef("multipart://upload")), prompt, null, null, "gemini-2.5-flash-image", 1, null, null, null, null),
                (com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity) servletRequest.getAttribute("apiKey")
        );
    }

    @PostMapping(value = "/messages", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    @Operation(summary = "Anthropic Messages compatibility")
    public Object anthropic(@Valid @RequestBody AnthropicDtos.AnthropicRequest request, HttpServletRequest servletRequest) {
        ChatCompletionRequest transformed = new ChatCompletionRequest(
                request.model(),
                request.messages().stream()
                        .map(message -> new ChatCompletionRequest.ChatMessageInput(
                                message.role(),
                                message.content() == null ? "" : message.content(),
                                message.name(),
                                message.tool_call_id(),
                                message.tool_calls()))
                        .toList(),
                request.temperature(),
                Integer.valueOf(request.max_tokens()),
                null,
                null,
                null,
                null,
                request.stream(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        if (request.streamEnabled()) {
            SseEmitter emitter = new SseEmitter(10_000L);
            new Thread(() -> relayStream(transformed, servletRequest, emitter, ProviderStreamFormat.ANTHROPIC)).start();
            return emitter;
        }
        ChatCompletionResponse response = chatGatewayService.complete(transformed, (com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity) servletRequest.getAttribute("apiKey"));
        return new AnthropicDtos.AnthropicResponse(
                response.id(),
                "message",
                "assistant",
                response.model(),
                List.of(Map.of("type", "text", "text", response.choices().get(0).message().content())),
                "end_turn",
                null,
                Map.of("input_tokens", response.usage().prompt_tokens(), "output_tokens", response.usage().completion_tokens())
        );
    }

    @PostMapping("/responses")
    public ResponseEntity<String> createResponse() {
        return ResponseEntity.status(404).body("The Responses API is currently not supported. Please use the /v1/chat/completions API route instead.");
    }

    @GetMapping("/responses/{responseId}")
    public ResponseEntity<String> getResponse(@PathVariable String responseId) {
        return ResponseEntity.status(404).body("The Responses API is currently not supported. Please use the /v1/chat/completions API route instead.");
    }

    private void relayStream(ChatCompletionRequest request, HttpServletRequest servletRequest, SseEmitter emitter, ProviderStreamFormat format) {
        try {
            chatGatewayService.stream(
                    request,
                    (com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity) servletRequest.getAttribute("apiKey"),
                    format,
                    event -> {
                        try {
                            if (event.eventName() != null && !event.eventName().isBlank()) {
                                emitter.send(SseEmitter.event().name(event.eventName()).data(event.data()));
                            } else {
                                emitter.send(SseEmitter.event().data(event.data()));
                            }
                            if (event.done()) {
                                emitter.complete();
                            }
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    }
            );
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }
}
