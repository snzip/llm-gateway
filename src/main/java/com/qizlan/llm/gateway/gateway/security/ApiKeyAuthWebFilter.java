package com.qizlan.llm.gateway.gateway.security;

import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class ApiKeyAuthWebFilter implements WebFilter {

    private static final String API_KEY_ATTR = "apiKey";

    private final GatewayProperties properties;
    private final ApiKeyTokenService tokenService;
    private final IamRuleService iamRuleService;
    private final ApiKeyRateLimitService apiKeyRateLimitService;
    private final ApiKeyLookupCache apiKeyLookupCache;

    public ApiKeyAuthWebFilter(
            GatewayProperties properties,
            ApiKeyTokenService tokenService,
            IamRuleService iamRuleService,
            ApiKeyRateLimitService apiKeyRateLimitService,
            ApiKeyLookupCache apiKeyLookupCache
    ) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.iamRuleService = iamRuleService;
        this.apiKeyRateLimitService = apiKeyRateLimitService;
        this.apiKeyLookupCache = apiKeyLookupCache;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!requiresApiKey(path)) {
            return chain.filter(exchange);
        }
        return Mono.fromCallable(() -> authenticate(exchange))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(apiKey -> {
                    exchange.getAttributes().put(API_KEY_ATTR, apiKey);
                    return chain.filter(exchange);
                })
                .onErrorResume(ApiKeyFailure.class, failure -> writeError(exchange, failure.status(), failure.getMessage()));
    }

    private boolean requiresApiKey(String path) {
        if ("/v1/models".equals(path)) {
            return false;
        }
        return path.startsWith("/v1/");
    }

    private ApiKeyEntity authenticate(ServerWebExchange exchange) {
        String headerValue = exchange.getRequest().getHeaders().getFirst(properties.apiKeyHeader());
        if (headerValue == null || !headerValue.startsWith("Bearer ")) {
            throw new ApiKeyFailure(HttpStatus.UNAUTHORIZED, "Missing Bearer token");
        }

        String token = headerValue.substring("Bearer ".length()).trim();
        ApiKeyEntity apiKey = apiKeyLookupCache.findActiveByTokenHash(tokenService.hash(token)).orElse(null);
        if (apiKey == null) {
            throw new ApiKeyFailure(HttpStatus.UNAUTHORIZED, "Invalid API key");
        }
        try {
            iamRuleService.assertBudgetAllowed(apiKey);
            iamRuleService.assertPathAllowed(apiKey, exchange.getRequest().getPath().value());
            IamRuleService.RateLimitPolicy rateLimitPolicy = iamRuleService.resolveRateLimit(apiKey);
            apiKeyRateLimitService.assertAllowed(apiKey, rateLimitPolicy);
            return apiKey;
        } catch (ApiKeyAccessDeniedException ex) {
            HttpStatus status = ex.getMessage().contains("rate limit") ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.FORBIDDEN;
            throw new ApiKeyFailure(status, ex.getMessage());
        }
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        byte[] body = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(body)));
    }

    private static final class ApiKeyFailure extends RuntimeException {
        private final HttpStatus status;

        private ApiKeyFailure(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        private HttpStatus status() {
            return status;
        }
    }
}
