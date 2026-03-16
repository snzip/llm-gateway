package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.service.OAuthService;
import com.qizlan.llm.gateway.persistence.entity.OAuthAccessTokenEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthAuthorizationCodeEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthClientEntity;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "MCP OAuth", description = "OAuth compatibility endpoints for MCP clients")
public class McpOAuthController {

    private final OAuthService oAuthService;
    private final Scheduler controlPlaneScheduler;

    public McpOAuthController(OAuthService oAuthService, Scheduler controlPlaneScheduler) {
        this.oAuthService = oAuthService;
        this.controlPlaneScheduler = controlPlaneScheduler;
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public Mono<Map<String, Object>> oauthMetadataRoot(org.springframework.web.server.ServerWebExchange exchange) {
        return blocking(() -> oauthMetadata(exchange.getRequest(), ""));
    }

    @GetMapping("/.well-known/oauth-authorization-server/mcp")
    public Mono<Map<String, Object>> oauthMetadataMcp(org.springframework.web.server.ServerWebExchange exchange) {
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
                    stringValue(request, "client_name", "mcp-client"),
                    request != null && request.get("grant_types") instanceof List<?> list
                            ? list.stream().map(Object::toString).toList()
                            : List.of("authorization_code", "client_credentials", "refresh_token"),
                    resolveRedirectUri(request)
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
                java.util.Map.entry("resource", base + resourceSuffix),
                java.util.Map.entry("scopes_supported", List.of("mcp", "mcp.tool.chat", "mcp.tool.generate-image", "mcp.tool.generate-nano-banana"))
        );
    }

    private String resolveRedirectUri(Map<String, Object> request) {
        if (request == null) {
            return null;
        }
        Object redirectUris = request.get("redirect_uris");
        if (redirectUris instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            return first == null ? null : first.toString();
        }
        Object redirectUri = request.get("redirect_uri");
        return redirectUri == null || redirectUri.toString().isBlank() ? null : redirectUri.toString();
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
