package com.qizlan.llm.gateway.gateway.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

@Service
public class RequestContextService {

    public static final String EXCHANGE_ATTRIBUTE = RequestContext.class.getName();
    public static final String REACTOR_CONTEXT_KEY = RequestContext.class.getName() + ".reactor";

    public RequestContext create(String correlationId, String actorType, String actorId) {
        return new RequestContext(
                correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : correlationId,
                UUID.randomUUID().toString().replace("-", ""),
                UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                actorType == null || actorType.isBlank() ? "system" : actorType,
                actorId == null || actorId.isBlank() ? "control-plane" : actorId
        );
    }

    public RequestContext defaultContext() {
        return create(null, null, null);
    }

    public RequestContext get(ServerWebExchange exchange) {
        RequestContext context = exchange.getAttribute(EXCHANGE_ATTRIBUTE);
        return context == null ? defaultContext() : context;
    }

    public void set(ServerWebExchange exchange, RequestContext context) {
        exchange.getAttributes().put(EXCHANGE_ATTRIBUTE, context);
    }

    public RequestContext withDefaultActor(RequestContext context, String actorType, String actorId) {
        if (!"system".equals(context.actorType()) || !"control-plane".equals(context.actorId())) {
            return context;
        }
        return new RequestContext(context.correlationId(), context.traceId(), context.spanId(), actorType, actorId);
    }
}
