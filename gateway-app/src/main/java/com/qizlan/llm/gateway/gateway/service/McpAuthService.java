package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.gateway.security.ApiKeyAccessDeniedException;
import com.qizlan.llm.gateway.gateway.security.ApiKeyLookupCache;
import com.qizlan.llm.gateway.gateway.security.ApiKeyRateLimitService;
import com.qizlan.llm.gateway.gateway.security.ApiKeyTokenService;
import com.qizlan.llm.gateway.gateway.security.IamRuleService;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.OAuthAccessTokenEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

@Service
public class McpAuthService {

    private final ApiKeyTokenService apiKeyTokenService;
    private final ApiKeyLookupCache apiKeyLookupCache;
    private final IamRuleService iamRuleService;
    private final ApiKeyRateLimitService apiKeyRateLimitService;
    private final OAuthService oAuthService;

    public McpAuthService(
            ApiKeyTokenService apiKeyTokenService,
            ApiKeyLookupCache apiKeyLookupCache,
            IamRuleService iamRuleService,
            ApiKeyRateLimitService apiKeyRateLimitService,
            OAuthService oAuthService
    ) {
        this.apiKeyTokenService = apiKeyTokenService;
        this.apiKeyLookupCache = apiKeyLookupCache;
        this.iamRuleService = iamRuleService;
        this.apiKeyRateLimitService = apiKeyRateLimitService;
        this.oAuthService = oAuthService;
    }

    public AuthenticatedPrincipal authenticate(ServerWebExchange exchange, String requiredScope) {
        String xApiKey = exchange.getRequest().getHeaders().getFirst("x-api-key");
        if (xApiKey != null && !xApiKey.isBlank()) {
            return AuthenticatedPrincipal.apiKey(authenticateApiKey(xApiKey, exchange));
        }

        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Bearer token or x-api-key");
        }

        String token = authorization.substring("Bearer ".length()).trim();
        ApiKeyEntity apiKey = apiKeyLookupCache.findActiveByTokenHash(apiKeyTokenService.hash(token)).orElse(null);
        if (apiKey != null) {
            return AuthenticatedPrincipal.apiKey(validateApiKey(apiKey, exchange));
        }

        try {
            OAuthAccessTokenEntity accessToken = oAuthService.validateAccessToken(token, requiredScope);
            return AuthenticatedPrincipal.oauth(accessToken);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        }
    }

    private ApiKeyEntity authenticateApiKey(String rawToken, ServerWebExchange exchange) {
        ApiKeyEntity apiKey = apiKeyLookupCache.findActiveByTokenHash(apiKeyTokenService.hash(rawToken)).orElse(null);
        if (apiKey == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");
        }
        return validateApiKey(apiKey, exchange);
    }

    private ApiKeyEntity validateApiKey(ApiKeyEntity apiKey, ServerWebExchange exchange) {
        try {
            iamRuleService.assertBudgetAllowed(apiKey);
            iamRuleService.assertPathAllowed(apiKey, exchange.getRequest().getPath().value());
            IamRuleService.RateLimitPolicy rateLimitPolicy = iamRuleService.resolveRateLimit(apiKey);
            apiKeyRateLimitService.assertAllowed(apiKey, rateLimitPolicy);
            return apiKey;
        } catch (ApiKeyAccessDeniedException ex) {
            HttpStatus status = ex.getMessage().contains("rate limit") ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.FORBIDDEN;
            throw new ResponseStatusException(status, ex.getMessage());
        }
    }

    public record AuthenticatedPrincipal(ApiKeyEntity apiKey, OAuthAccessTokenEntity accessToken, String authenticationType) {
        public static AuthenticatedPrincipal apiKey(ApiKeyEntity apiKey) {
            return new AuthenticatedPrincipal(apiKey, null, "api_key");
        }

        public static AuthenticatedPrincipal oauth(OAuthAccessTokenEntity accessToken) {
            return new AuthenticatedPrincipal(null, accessToken, "oauth");
        }
    }
}
