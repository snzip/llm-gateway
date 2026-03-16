package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.dto.AnthropicDtos;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionResponse;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import com.qizlan.llm.gateway.gateway.provider.ProviderStreamFormat;
import com.qizlan.llm.gateway.gateway.service.ChatGatewayService;
import com.qizlan.llm.gateway.gateway.service.ImageGatewayService;
import com.qizlan.llm.gateway.gateway.service.ModelCatalogService;
import com.qizlan.llm.gateway.gateway.service.RequestContextService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/v1")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Data Plane", description = "聊天/图片/Anthropic 数据面接口")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "BearerAuth")
public class V1Controller {

    private final ChatGatewayService chatGatewayService;
    private final ModelCatalogService modelCatalogService;
    private final ImageGatewayService imageGatewayService;
    private final RequestContextService requestContextService;

    public V1Controller(ChatGatewayService chatGatewayService, ModelCatalogService modelCatalogService, ImageGatewayService imageGatewayService, RequestContextService requestContextService) {
        this.chatGatewayService = chatGatewayService;
        this.modelCatalogService = modelCatalogService;
        this.imageGatewayService = imageGatewayService;
        this.requestContextService = requestContextService;
    }

    @GetMapping("/models")
    @Operation(summary = "List all available models", description = "Returns the gateway-fused catalog of models, including metadata such as provider, context window, and pricing flags.")
    public Mono<Map<String, Object>> models() {
        return blocking(() -> Map.of("data", modelCatalogService.listModels()));
    }

    @PostMapping(value = "/chat/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    @Operation(summary = "Create a completion for the chat conversation", description = "Sends chat messages to the routed provider, supports tool calls, reasoning, and JSON streaming responses under `/v1/chat/completions`.")
    public Object chat(@Valid @RequestBody ChatCompletionRequest request, ServerWebExchange exchange) {
        if (!request.streamEnabled()) {
            return chatGatewayService.completeAsync(request, exchange.getAttribute("apiKey"), requestContextService.get(exchange));
        }
        return relayStream(request, exchange, ProviderStreamFormat.OPENAI);
    }

    @PostMapping("/images/generations")
    @Operation(summary = "Create image", description = "Generate an image from text prompts via the routed provider. Outputs base64-encoded image data.")
    public Mono<ImageDtos.ImageResponse> imageGeneration(@Valid @RequestBody ImageDtos.ImageGenerationRequest request, ServerWebExchange exchange) {
        return imageGatewayService.generateAsync(request, exchange.getAttribute("apiKey"), requestContextService.get(exchange));
    }

    @PostMapping(value = "/images/edits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Edit image", description = "Performs image editing by blending the provided prompt with the supplied image or multipart payload, returning revised assets.")
    public Mono<ImageDtos.ImageResponse> imageEditJson(@Valid @RequestBody ImageDtos.ImageEditRequest request, ServerWebExchange exchange) {
        return imageGatewayService.editAsync(request, exchange.getAttribute("apiKey"), requestContextService.get(exchange));
    }

    @PostMapping(value = "/images/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ImageDtos.ImageResponse> imageEditMultipart(
            @RequestPart("prompt") String prompt,
            @RequestPart(value = "image", required = false) FilePart image,
            @RequestPart(value = "image[]", required = false) FilePart imageArray,
            @RequestPart(value = "file", required = false) FilePart file,
            ServerWebExchange exchange
    ) {
        FilePart selected = image != null ? image : imageArray != null ? imageArray : file;
        if (!StringUtils.hasText(prompt) || selected == null || !StringUtils.hasText(selected.filename())) {
            throw new IllegalArgumentException("prompt and image file are required");
        }
        return imageGatewayService.editAsync(
                new ImageDtos.ImageEditRequest(List.of(new ImageDtos.ImageEditRef("multipart://upload")), prompt, null, null, "gemini-2.5-flash-image", 1, null, null, null, null),
                exchange.getAttribute("apiKey"),
                requestContextService.get(exchange)
        );
    }

    @PostMapping(value = "/messages", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    @Operation(summary = "Anthropic Messages compatibility", description = "Adapter endpoint for Anthropic-style `/v1/messages`. Converts incoming Anthropic message structure into the internal chat completion flow.")
    public Object anthropic(@Valid @RequestBody AnthropicDtos.AnthropicRequest request, ServerWebExchange exchange) {
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
            return relayStream(transformed, exchange, ProviderStreamFormat.ANTHROPIC);
        }
        return chatGatewayService.completeAsync(transformed, exchange.getAttribute("apiKey"), requestContextService.get(exchange))
                .map(response -> new AnthropicDtos.AnthropicResponse(
                    response.id(),
                    "message",
                    "assistant",
                    response.model(),
                    List.of(Map.of("type", "text", "text", response.choices().get(0).message().content())),
                    "end_turn",
                    null,
                    Map.of("input_tokens", response.usage().prompt_tokens(), "output_tokens", response.usage().completion_tokens())
                ));
    }

    @PostMapping("/responses")
    public ResponseEntity<String> createResponse() {
        return ResponseEntity.status(404).body("The Responses API is currently not supported. Please use the /v1/chat/completions API route instead.");
    }

    @GetMapping("/responses/{responseId}")
    public ResponseEntity<String> getResponse(@PathVariable String responseId) {
        return ResponseEntity.status(404).body("The Responses API is currently not supported. Please use the /v1/chat/completions API route instead.");
    }

    private Flux<ServerSentEvent<String>> relayStream(ChatCompletionRequest request, ServerWebExchange exchange, ProviderStreamFormat format) {
        return chatGatewayService.streamFlux(
                request,
                exchange.getAttribute("apiKey"),
                format,
                requestContextService.get(exchange)
        ).map(event -> {
            ServerSentEvent.Builder<String> builder = ServerSentEvent.builder(event.data());
            if (event.eventName() != null && !event.eventName().isBlank()) {
                builder.event(event.eventName());
            }
            return builder.build();
        });
    }

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic());
    }
}
