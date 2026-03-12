package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.service.McpService;
import com.qizlan.llm.gateway.gateway.service.McpSessionService;
import com.qizlan.llm.gateway.gateway.service.OAuthService;
import com.qizlan.llm.gateway.gateway.service.ProviderProbeService;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthAccessTokenEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthAuthorizationCodeEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthClientEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpController {

    private final ProviderProbeService providerProbeService;
    private final McpService mcpService;
    private final McpSessionService mcpSessionService;
    private final OAuthService oAuthService;

    public McpController(ProviderProbeService providerProbeService, McpService mcpService, McpSessionService mcpSessionService, OAuthService oAuthService) {
        this.providerProbeService = providerProbeService;
        this.mcpService = mcpService;
        this.mcpSessionService = mcpSessionService;
        this.oAuthService = oAuthService;
    }

    @PostMapping("/internal/providers/probe")
    public Map<String, Object> probeProviders(@RequestParam(name = "provider", required = false) String provider) {
        if (provider == null || provider.isBlank()) {
            return Map.of("data", providerProbeService.probeAll());
        }
        return Map.of("data", providerProbeService.probeProvider(provider));
    }

    @GetMapping("/internal/providers/probe/history")
    public Map<String, Object> probeHistory(@RequestParam(name = "provider", required = false) String provider) {
        return Map.of("data", providerProbeService.history(provider).stream().map(item -> Map.of(
                "id", item.getId(),
                "provider_id", item.getProviderId(),
                "healthy", item.isHealthy(),
                "latency_ms", item.getLatencyMs(),
                "probe_model", item.getProbeModel(),
                "strategy", item.getStrategy(),
                "error_message", item.getErrorMessage() == null ? "" : item.getErrorMessage(),
                "created_at", item.getCreatedAt().toString()
        )).toList());
    }

    @RequestMapping("/mcp")
    public Map<String, Object> mcp(@RequestBody(required = false) Map<String, Object> request, HttpServletRequest servletRequest) {
        if (request == null || request.isEmpty()) {
            return Map.of(
                    "server", "llmgatejava-mcp",
                    "version", "0.0.1-SNAPSHOT",
                    "tools", mcpService.listTools()
            );
        }
        String method = request.getOrDefault("method", "").toString();
        ApiKeyEntity apiKey = (ApiKeyEntity) servletRequest.getAttribute("apiKey");
        return switch (method) {
            case "initialize" -> mcpSessionService.initialize(request);
            case "ping" -> mcpSessionService.ping(request.getOrDefault("session_id", "").toString());
            case "tools/list" -> Map.of("tools", mcpService.listTools());
            case "tools/call" -> Map.of(
                    "tool", request.getOrDefault("name", ""),
                    "result", mcpService.callTool(
                            request.getOrDefault("name", "").toString(),
                            request.get("arguments") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(),
                            apiKey));
            default -> Map.of("error", true, "message", "Unsupported MCP method: " + method);
        };
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public Map<String, Object> oauthMetadataRoot(HttpServletRequest request) {
        return oauthMetadata(request, "");
    }

    @GetMapping("/.well-known/oauth-authorization-server/mcp")
    public Map<String, Object> oauthMetadataMcp(HttpServletRequest request) {
        return oauthMetadata(request, "/mcp");
    }

    @GetMapping("/oauth/authorize")
    public ResponseEntity<Map<String, Object>> authorize(
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "state", required = false) String state
    ) {
        OAuthAuthorizationCodeEntity code = oAuthService.issueAuthorizationCode(clientId == null ? "" : clientId, redirectUri, state);
        return ResponseEntity.ok(Map.of(
                "code", code.getCode(),
                "client_id", clientId == null ? "" : clientId,
                "redirect_uri", redirectUri == null ? "" : redirectUri,
                "state", state == null ? "" : state
        ));
    }

    @PostMapping("/oauth/token")
    public Map<String, Object> token(@RequestBody(required = false) Map<String, Object> request) {
        OAuthAccessTokenEntity token = oAuthService.exchangeToken(request == null ? Map.of() : request);
        return Map.of(
                "access_token", token.getAccessToken(),
                "token_type", "Bearer",
                "expires_in", token.getExpiresInSeconds(),
                "scope", token.getScope(),
                "client_id", token.getClientId()
        );
    }

    @PostMapping("/oauth/register")
    public Map<String, Object> register(@RequestBody(required = false) Map<String, Object> request) {
        OAuthClientEntity client = oAuthService.registerClient(
                request == null ? null : request.getOrDefault("client_name", null) == null ? null : request.get("client_name").toString(),
                request != null && request.get("grant_types") instanceof java.util.List<?> list ? list.stream().map(Object::toString).toList() : java.util.List.of("authorization_code", "client_credentials"),
                request == null ? null : request.getOrDefault("redirect_uri", null) == null ? null : request.get("redirect_uri").toString()
        );
        return Map.of(
                "client_id", client.getClientId(),
                "client_secret", client.getClientSecret(),
                "client_name", client.getClientName(),
                "grant_types", client.getGrantTypes().split(" "),
                "redirect_uri", client.getRedirectUri() == null ? "" : client.getRedirectUri()
        );
    }

    private Map<String, Object> oauthMetadata(HttpServletRequest request, String resourceSuffix) {
        String base = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        return Map.of(
                "issuer", base,
                "authorization_endpoint", base + "/oauth/authorize",
                "token_endpoint", base + "/oauth/token",
                "registration_endpoint", base + "/oauth/register",
                "resource", base + resourceSuffix
        );
    }
}
