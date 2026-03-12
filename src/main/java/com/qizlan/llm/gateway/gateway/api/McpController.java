package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.service.McpService;
import com.qizlan.llm.gateway.gateway.service.McpSessionService;
import com.qizlan.llm.gateway.gateway.service.OAuthService;
import com.qizlan.llm.gateway.gateway.service.ProviderProbeService;
import com.qizlan.llm.gateway.gateway.service.RequestContextService;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthAccessTokenEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthAuthorizationCodeEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthClientEntity;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class McpController {

    private final ProviderProbeService providerProbeService;
    private final McpService mcpService;
    private final McpSessionService mcpSessionService;
    private final OAuthService oAuthService;
    private final RequestContextService requestContextService;

    public McpController(
            ProviderProbeService providerProbeService,
            McpService mcpService,
            McpSessionService mcpSessionService,
            OAuthService oAuthService,
            RequestContextService requestContextService
    ) {
        this.providerProbeService = providerProbeService;
        this.mcpService = mcpService;
        this.mcpSessionService = mcpSessionService;
        this.oAuthService = oAuthService;
        this.requestContextService = requestContextService;
    }

    @PostMapping("/internal/providers/probe")
    public Mono<Map<String, Object>> probeProviders(@RequestParam(name = "provider", required = false) String provider) {
        return blocking(() -> {
            if (provider == null || provider.isBlank()) {
                return Map.of("data", providerProbeService.probeAll());
            }
            return Map.of("data", providerProbeService.probeProvider(provider));
        });
    }

    @GetMapping("/internal/providers/probe/history")
    public Mono<Map<String, Object>> probeHistory(@RequestParam(name = "provider", required = false) String provider) {
        return blocking(() -> Map.of("data", providerProbeService.history(provider).stream().map(item -> Map.of(
                "id", item.getId(),
                "provider_id", item.getProviderId(),
                "healthy", item.isHealthy(),
                "latency_ms", item.getLatencyMs(),
                "probe_model", item.getProbeModel(),
                "strategy", item.getStrategy(),
                "error_message", item.getErrorMessage() == null ? "" : item.getErrorMessage(),
                "created_at", item.getCreatedAt().toString()
        )).toList()));
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public Mono<Map<String, Object>> oauthMetadataRoot(ServerWebExchange exchange) {
        return blocking(() -> oauthMetadata(exchange.getRequest(), ""));
    }

    @GetMapping("/.well-known/oauth-authorization-server/mcp")
    public Mono<Map<String, Object>> oauthMetadataMcp(ServerWebExchange exchange) {
        return blocking(() -> oauthMetadata(exchange.getRequest(), "/mcp"));
    }

    @GetMapping("/oauth/authorize")
    public Mono<ResponseEntity<Map<String, Object>>> authorize(
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "state", required = false) String state
    ) {
        return blocking(() -> {
            OAuthAuthorizationCodeEntity code = oAuthService.issueAuthorizationCode(clientId == null ? "" : clientId, redirectUri, state);
            return ResponseEntity.ok(Map.of(
                    "code", code.getCode(),
                    "client_id", clientId == null ? "" : clientId,
                    "redirect_uri", redirectUri == null ? "" : redirectUri,
                    "state", state == null ? "" : state
            ));
        });
    }

    @PostMapping("/oauth/token")
    public Mono<Map<String, Object>> token(@RequestBody(required = false) Map<String, Object> request) {
        return blocking(() -> {
            OAuthAccessTokenEntity token = oAuthService.exchangeToken(request == null ? Map.of() : request);
            return java.util.Map.ofEntries(
                    java.util.Map.entry("access_token", token.getAccessToken()),
                    java.util.Map.entry("token_type", "Bearer"),
                    java.util.Map.entry("expires_in", token.getExpiresInSeconds()),
                    java.util.Map.entry("scope", token.getScope()),
                    java.util.Map.entry("client_id", token.getClientId()),
                    java.util.Map.entry("refresh_token", token.getRefreshToken() == null ? "" : token.getRefreshToken())
            );
        });
    }

    @PostMapping("/oauth/revoke")
    public Mono<ResponseEntity<Void>> revoke(@RequestBody(required = false) Map<String, Object> request) {
        return blocking(() -> {
            oAuthService.revokeToken(request == null ? Map.of() : request);
            return ResponseEntity.noContent().build();
        });
    }

    @PostMapping("/oauth/register")
    public Mono<Map<String, Object>> register(@RequestBody(required = false) Map<String, Object> request) {
        return blocking(() -> {
            OAuthClientEntity client = oAuthService.registerClient(
                    request == null ? null : request.getOrDefault("client_name", null) == null ? null : request.get("client_name").toString(),
                    request != null && request.get("grant_types") instanceof java.util.List<?> list ? list.stream().map(Object::toString).toList() : java.util.List.of("authorization_code", "client_credentials", "refresh_token"),
                    request == null ? null : request.getOrDefault("redirect_uri", null) == null ? null : request.get("redirect_uri").toString()
            );
            return Map.of(
                    "client_id", client.getClientId(),
                    "client_secret", client.getClientSecret(),
                    "client_name", client.getClientName(),
                    "grant_types", client.getGrantTypes().split(" "),
                    "redirect_uri", client.getRedirectUri() == null ? "" : client.getRedirectUri()
            );
        });
    }

    @PostMapping("/oauth/clients/{clientId}/rotate-secret")
    public Mono<Map<String, Object>> rotateSecret(@PathVariable("clientId") String clientId) {
        return blocking(() -> {
            OAuthClientEntity client = oAuthService.rotateClientSecret(clientId);
            return Map.of(
                    "client_id", client.getClientId(),
                    "client_secret", client.getClientSecret(),
                    "rotated", true
            );
        });
    }

    private Map<String, Object> oauthMetadata(ServerHttpRequest request, String resourceSuffix) {
        URI uri = request.getURI();
        String authority = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        String base = uri.getScheme() + "://" + authority;
        return java.util.Map.ofEntries(
                java.util.Map.entry("issuer", base),
                java.util.Map.entry("authorization_endpoint", base + "/oauth/authorize"),
                java.util.Map.entry("token_endpoint", base + "/oauth/token"),
                java.util.Map.entry("revocation_endpoint", base + "/oauth/revoke"),
                java.util.Map.entry("registration_endpoint", base + "/oauth/register"),
                java.util.Map.entry("resource", base + resourceSuffix)
        );
    }

    @RequestMapping("/mcp")
    public Mono<Map<String, Object>> mcpProtected(@RequestBody(required = false) Map<String, Object> request, ServerWebExchange exchange) {
        return blocking(() -> mcpInternal(request, exchange));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mcpInternal(Map<String, Object> request, ServerWebExchange exchange) {
        if (request == null || request.isEmpty()) {
            return Map.of(
                    "server", "llmgatejava-mcp",
                    "version", "0.0.1-SNAPSHOT",
                    "tools", mcpService.listTools()
            );
        }
        String method = request.getOrDefault("method", "").toString();
        ApiKeyEntity apiKey = exchange.getAttribute("apiKey");
        if ("tools/call".equals(method)) {
            OAuthAccessTokenEntity token = requireOAuthAccessToken(exchange);
            requestContextService.set(exchange, requestContextService.withDefaultActor(requestContextService.get(exchange), "oauth_client", token.getClientId()));
        }
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

    private OAuthAccessTokenEntity requireOAuthAccessToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing OAuth bearer token");
        }
        try {
            return oAuthService.validateAccessToken(header.substring("Bearer ".length()).trim(), "mcp");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        }
    }

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic());
    }
}
