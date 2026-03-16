package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.service.McpAuthService;
import com.qizlan.llm.gateway.gateway.service.McpService;
import com.qizlan.llm.gateway.gateway.service.McpSessionService;
import com.qizlan.llm.gateway.gateway.service.RequestContextService;
import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthAccessTokenEntity;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "MCP", description = "Model Context Protocol runtime endpoint")
public class McpController {

    private final McpService mcpService;
    private final McpSessionService mcpSessionService;
    private final McpAuthService mcpAuthService;
    private final RequestContextService requestContextService;
    private final GatewayProperties properties;
    private final Scheduler controlPlaneScheduler;

    public McpController(
            McpService mcpService,
            McpSessionService mcpSessionService,
            McpAuthService mcpAuthService,
            RequestContextService requestContextService,
            GatewayProperties properties,
            Scheduler controlPlaneScheduler
    ) {
        this.mcpService = mcpService;
        this.mcpSessionService = mcpSessionService;
        this.mcpAuthService = mcpAuthService;
        this.requestContextService = requestContextService;
        this.properties = properties;
        this.controlPlaneScheduler = controlPlaneScheduler;
    }

    @RequestMapping("/mcp")
    public Mono<Map<String, Object>> mcp(@RequestBody(required = false) Map<String, Object> request, ServerWebExchange exchange) {
        return blocking(() -> mcpInternal(request, exchange));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mcpInternal(Map<String, Object> request, ServerWebExchange exchange) {
        String method = request == null ? "" : stringValue(request, "method", "");
        String toolName = request == null ? "" : stringValue(request, "name", "");
        McpAuthService.AuthenticatedPrincipal principal = mcpAuthService.authenticate(exchange, requiredScope(method, toolName));

        if (principal.accessToken() != null) {
            requestContextService.set(exchange, requestContextService.withDefaultActor(
                    requestContextService.get(exchange),
                    "oauth_client",
                    principal.accessToken().getClientId()
            ));
        }

        if (request == null || request.isEmpty()) {
            return Map.of(
                    "server", "llmgatejava-mcp",
                    "version", "0.0.1-SNAPSHOT",
                    "authenticated_via", principal.authenticationType(),
                    "tools", mcpService.listTools()
            );
        }

        ApiKeyEntity apiKey = principal.apiKey();
        return switch (method) {
            case "initialize" -> mcpSessionService.initialize(request, clientName(request));
            case "ping" -> mcpSessionService.ping(resolveSessionId(request, exchange.getRequest()));
            case "tools/list" -> Map.of(
                    "tools", mcpService.listTools(),
                    "authenticated_via", principal.authenticationType()
            );
            case "tools/call" -> Map.of(
                    "tool", toolName,
                    "result", mcpService.callTool(
                            toolName,
                            request.get("arguments") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(),
                            apiKey
                    ),
                    "authenticated_via", principal.authenticationType()
            );
            default -> Map.of("error", true, "message", "Unsupported MCP method: " + method);
        };
    }

    private String requiredScope(String method, String toolName) {
        if ("tools/call".equals(method)) {
            String mapped = properties.mcp().toolScopes().get(toolName);
            return mapped == null || mapped.isBlank() ? "mcp" : mapped;
        }
        return "mcp";
    }

    private String resolveSessionId(Map<String, Object> request, ServerHttpRequest httpRequest) {
        String sessionId = stringValue(request, "session_id", "");
        if (!sessionId.isBlank()) {
            return sessionId;
        }
        String headerSessionId = httpRequest.getHeaders().getFirst("mcp-session-id");
        if (headerSessionId != null && !headerSessionId.isBlank()) {
            return headerSessionId;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing MCP session_id");
    }

    @SuppressWarnings("unchecked")
    private String clientName(Map<String, Object> request) {
        Object client = request.get("client");
        if (client instanceof Map<?, ?> clientMap) {
            Object name = clientMap.get("name");
            if (name != null && !name.toString().isBlank()) {
                return name.toString();
            }
        }
        return stringValue(request, "client_name", "unknown-client");
    }

    private String stringValue(Map<String, Object> request, String key, String fallback) {
        if (request == null) {
            return fallback;
        }
        Object value = request.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(controlPlaneScheduler);
    }
}
