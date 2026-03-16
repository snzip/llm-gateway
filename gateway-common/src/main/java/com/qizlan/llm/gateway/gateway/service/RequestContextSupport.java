package com.qizlan.llm.gateway.gateway.service;

import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;

@Service
public class RequestContextSupport {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    public static final String ACTOR_TYPE_HEADER = "X-Actor-Type";
    public static final String ACTOR_ID_HEADER = "X-Actor-Id";
    public static final String REQUEST_ATTRIBUTE = RequestContext.class.getName();
    public static final String EXCHANGE_ATTRIBUTE = RequestContext.class.getName();
    public static final String REACTOR_CONTEXT_KEY = RequestContext.class.getName() + ".reactor";

    public RequestContext create(String correlationId, String actorType, String actorId) {
        return new RequestContext(
                valueOrRandom(correlationId),
                randomId(),
                randomId().substring(0, 16),
                valueOrDefault(actorType, "system"),
                valueOrDefault(actorId, "control-plane")
        );
    }

    public RequestContext defaultContext() {
        return create(null, null, null);
    }

    public RequestContext withDefaultActor(RequestContext context, String actorType, String actorId) {
        if (!"system".equals(context.actorType()) || !"control-plane".equals(context.actorId())) {
            return context;
        }
        return new RequestContext(context.correlationId(), context.traceId(), context.spanId(), actorType, actorId);
    }

    public void writeResponseHeaders(BiConsumer<String, String> headerWriter, RequestContext context) {
        headerWriter.accept(CORRELATION_ID_HEADER, context.correlationId());
        headerWriter.accept(TRACE_ID_HEADER, context.traceId());
        headerWriter.accept(SPAN_ID_HEADER, context.spanId());
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String valueOrRandom(String value) {
        return value == null || value.isBlank() ? randomId() : value;
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
