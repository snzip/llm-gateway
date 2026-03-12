package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.service.McpService;
import com.qizlan.llm.gateway.gateway.service.ProviderProbeService;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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

    public McpController(ProviderProbeService providerProbeService, McpService mcpService) {
        this.providerProbeService = providerProbeService;
        this.mcpService = mcpService;
    }

    @PostMapping("/internal/providers/probe")
    public Map<String, Object> probeProviders(@RequestParam(name = "provider", required = false) String provider) {
        if (provider == null || provider.isBlank()) {
            return Map.of("data", providerProbeService.probeAll());
        }
        return Map.of("data", providerProbeService.probeProvider(provider));
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
        return ResponseEntity.ok(Map.of(
                "code", "mcp-auth-code",
                "client_id", clientId == null ? "" : clientId,
                "redirect_uri", redirectUri == null ? "" : redirectUri,
                "state", state == null ? "" : state
        ));
    }

    @PostMapping("/oauth/token")
    public Map<String, Object> token(@RequestBody(required = false) Map<String, Object> request) {
        return Map.of(
                "access_token", "mcp_" + UUID.randomUUID().toString().replace("-", ""),
                "token_type", "Bearer",
                "expires_in", 3600,
                "scope", request == null ? "mcp" : request.getOrDefault("scope", "mcp")
        );
    }

    @PostMapping("/oauth/register")
    public Map<String, Object> register(@RequestBody(required = false) Map<String, Object> request) {
        return Map.of(
                "client_id", "mcp-client-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "client_name", request == null ? "llmgateway-client" : request.getOrDefault("client_name", "llmgateway-client"),
                "grant_types", request == null ? java.util.List.of("authorization_code", "client_credentials") : request.getOrDefault("grant_types", java.util.List.of("authorization_code", "client_credentials"))
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
