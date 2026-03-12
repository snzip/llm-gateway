package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.dto.ImageDtos;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class McpService {

    private final ModelCatalogService modelCatalogService;
    private final ChatGatewayService chatGatewayService;
    private final ImageGatewayService imageGatewayService;

    public McpService(ModelCatalogService modelCatalogService, ChatGatewayService chatGatewayService, ImageGatewayService imageGatewayService) {
        this.modelCatalogService = modelCatalogService;
        this.chatGatewayService = chatGatewayService;
        this.imageGatewayService = imageGatewayService;
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
                tool("chat", "Chat completion through the gateway"),
                tool("list-models", "List gateway chat models"),
                tool("generate-image", "Generate image through the gateway"),
                tool("generate-nano-banana", "Alias image generator tool"),
                tool("list-image-models", "List gateway image models")
        );
    }

    public Object callTool(String name, Map<String, Object> arguments, ApiKeyEntity apiKey) {
        return switch (name) {
            case "list-models" -> modelCatalogService.listModels().stream()
                    .filter(model -> !model.providers().isEmpty())
                    .filter(model -> !model.architecture().output_modalities().contains("image") || model.architecture().output_modalities().contains("text"))
                    .toList();
            case "list-image-models" -> modelCatalogService.listModels().stream()
                    .filter(model -> model.architecture().output_modalities().contains("image"))
                    .toList();
            case "chat" -> chat(arguments, apiKey);
            case "generate-image", "generate-nano-banana" -> generateImage(arguments, apiKey);
            default -> throw new IllegalArgumentException("Unknown MCP tool: " + name);
        };
    }

    private Object chat(Map<String, Object> arguments, ApiKeyEntity apiKey) {
        String model = stringArg(arguments, "model", "gpt-4o");
        String prompt = stringArg(arguments, "prompt", "hello");
        return chatGatewayService.complete(new ChatCompletionRequest(
                model,
                List.of(new ChatCompletionRequest.ChatMessageInput("user", prompt, null, null, null)),
                null, null, null, null, null, null, false,
                null, null, null, null, null, null, null, null, null, null, null, null
        ), apiKey);
    }

    private Object generateImage(Map<String, Object> arguments, ApiKeyEntity apiKey) {
        String model = stringArg(arguments, "model", "gemini-2.5-flash-image");
        String prompt = stringArg(arguments, "prompt", "draw a banana");
        return imageGatewayService.generate(new ImageDtos.ImageGenerationRequest(prompt, model, 1, null, null, null, null, null), apiKey);
    }

    private Map<String, Object> tool(String name, String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("description", description);
        return item;
    }

    private String stringArg(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments == null ? null : arguments.get(key);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }
}
