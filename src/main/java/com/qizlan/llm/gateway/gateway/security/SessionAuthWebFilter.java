package com.qizlan.llm.gateway.gateway.security;

import com.qizlan.llm.gateway.gateway.service.SessionService;
import com.qizlan.llm.gateway.persistence.entity.SessionEntity;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import com.qizlan.llm.gateway.config.GatewayProperties;

@Component
@Order(1)
public class SessionAuthWebFilter implements WebFilter {

    private final SessionService sessionService;
    private final Scheduler controlPlaneScheduler;
    private final ApiKeyPathPolicy pathPolicy;
    private final GatewayProperties properties;

    public SessionAuthWebFilter(SessionService sessionService,
            ApiKeyPathPolicy pathPolicy,
            GatewayProperties properties,
            Scheduler controlPlaneScheduler) {
        this.sessionService = sessionService;
        this.pathPolicy = pathPolicy;
        this.properties = properties;
        this.controlPlaneScheduler = controlPlaneScheduler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!requiresSession(path, exchange)) {
            return chain.filter(exchange);
        }
        return Mono.fromCallable(() -> authenticate(exchange))
                .subscribeOn(controlPlaneScheduler)
                .flatMap(session -> {
                    sessionService.bind(exchange, session);
                    return chain.filter(exchange);
                })
                .onErrorResume(SessionAuthException.class, ex -> writeError(exchange, ex.status(), ex.getMessage()));
    }

    private boolean requiresSession(String path, ServerWebExchange exchange) {
        if (pathPolicy.requiresApiKey(path) && hasApiKeyHeader(exchange)) {
            return false;
        }
        if (path.startsWith("/auth/login") || path.startsWith("/auth")) {
            return !"/auth/login".equals(path);
        }
        if (path.startsWith("/user")) {
            return true;
        }
        if (path.startsWith("/v1") || path.startsWith("/metrics") || path.startsWith("/json") || path.startsWith("/docs") || path.startsWith("/.well-known")
                || path.startsWith("/mcp") || path.startsWith("/oauth")) {
            return false;
        }
        return path.startsWith("/orgs")
                || path.startsWith("/projects")
                || path.startsWith("/keys")
                || path.startsWith("/logs")
                || path.startsWith("/costs")
                || path.startsWith("/guardrails")
                || path.startsWith("/audit-logs")
                || path.startsWith("/admin")
                || path.startsWith("/team")
                || path.startsWith("/payments")
                || path.startsWith("/subscriptions")
                || path.startsWith("/dev-plans")
                || path.startsWith("/beacon")
                || path.startsWith("/referral")
                || path.startsWith("/public");
    }

    private boolean hasApiKeyHeader(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(properties.apiKeyHeader());
        return header != null && header.startsWith("Bearer ");
    }

    private SessionEntity authenticate(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new SessionAuthException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        String token = header.substring("Bearer ".length()).trim();
        return sessionService.validate(token).orElseThrow(() -> new SessionAuthException(HttpStatus.UNAUTHORIZED, "Invalid session token"));
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        byte[] body = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private static final class SessionAuthException extends RuntimeException {
        private final HttpStatus status;

        private SessionAuthException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        private HttpStatus status() {
            return status;
        }
    }
}
