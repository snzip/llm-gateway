package com.qizlan.llm.gateway.config;

import com.qizlan.llm.gateway.gateway.service.RequestContextService;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component("gatewayRequestContextFilter")
public class RequestContextFilter implements WebFilter {

    private final RequestContextService requestContextService;

    public RequestContextFilter(RequestContextService requestContextService) {
        this.requestContextService = requestContextService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var context = requestContextService.create(
                exchange.getRequest().getHeaders().getFirst("X-Correlation-Id"),
                exchange.getRequest().getHeaders().getFirst("X-Actor-Type"),
                exchange.getRequest().getHeaders().getFirst("X-Actor-Id")
        );
        requestContextService.set(exchange, context);
        exchange.getResponse().getHeaders().set("X-Correlation-Id", context.correlationId());
        exchange.getResponse().getHeaders().set("X-Trace-Id", context.traceId());
        exchange.getResponse().getHeaders().set("X-Span-Id", context.spanId());
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(RequestContextService.REACTOR_CONTEXT_KEY, context));
    }
}
