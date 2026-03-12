package com.qizlan.llm.gateway.config;

import com.qizlan.llm.gateway.gateway.service.GatewayMetricsService;
import com.qizlan.llm.gateway.gateway.service.RequestContext;
import com.qizlan.llm.gateway.gateway.service.RequestContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AccessLogWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogWebFilter.class);

    private final RequestContextService requestContextService;
    private final GatewayMetricsService gatewayMetricsService;

    public AccessLogWebFilter(RequestContextService requestContextService, GatewayMetricsService gatewayMetricsService) {
        this.requestContextService = requestContextService;
        this.gatewayMetricsService = gatewayMetricsService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedAt = System.currentTimeMillis();
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long latencyMs = System.currentTimeMillis() - startedAt;
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int status = statusCode == null ? 200 : statusCode.value();
                    gatewayMetricsService.recordHttpExchange(
                            exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name(),
                            exchange.getRequest().getPath().value(),
                            status,
                            latencyMs
                    );
                    RequestContext context = requestContextService.get(exchange);
                    log.info(
                            "http_access correlation_id={} actor_type={} actor_id={} method={} path={} status={} latency_ms={}",
                            context.correlationId(),
                            context.actorType(),
                            context.actorId(),
                            exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name(),
                            exchange.getRequest().getPath().value(),
                            status,
                            latencyMs
                    );
                });
    }
}
