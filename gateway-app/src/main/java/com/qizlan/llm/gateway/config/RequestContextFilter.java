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
                exchange.getRequest().getHeaders().getFirst(RequestContextService.CORRELATION_ID_HEADER),
                exchange.getRequest().getHeaders().getFirst(RequestContextService.ACTOR_TYPE_HEADER),
                exchange.getRequest().getHeaders().getFirst(RequestContextService.ACTOR_ID_HEADER)
        );
        requestContextService.set(exchange, context);
        requestContextService.writeResponseHeaders(exchange.getResponse().getHeaders()::set, context);
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(RequestContextService.REACTOR_CONTEXT_KEY, context));
    }
}
