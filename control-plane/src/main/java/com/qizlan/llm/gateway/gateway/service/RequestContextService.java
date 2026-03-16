package com.qizlan.llm.gateway.gateway.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class RequestContextService {

    public static final String REQUEST_ATTRIBUTE = RequestContextSupport.REQUEST_ATTRIBUTE;
    public static final String CORRELATION_ID_HEADER = RequestContextSupport.CORRELATION_ID_HEADER;
    public static final String TRACE_ID_HEADER = RequestContextSupport.TRACE_ID_HEADER;
    public static final String SPAN_ID_HEADER = RequestContextSupport.SPAN_ID_HEADER;
    public static final String ACTOR_TYPE_HEADER = RequestContextSupport.ACTOR_TYPE_HEADER;
    public static final String ACTOR_ID_HEADER = RequestContextSupport.ACTOR_ID_HEADER;

    private final RequestContextSupport requestContextSupport;

    public RequestContextService(RequestContextSupport requestContextSupport) {
        this.requestContextSupport = requestContextSupport;
    }

    public RequestContext create(String correlationId, String actorType, String actorId) {
        return requestContextSupport.create(correlationId, actorType, actorId);
    }

    public RequestContext defaultContext() {
        return requestContextSupport.defaultContext();
    }

    public RequestContext get(HttpServletRequest request) {
        Object context = request.getAttribute(REQUEST_ATTRIBUTE);
        return context instanceof RequestContext requestContext ? requestContext : defaultContext();
    }

    public void set(HttpServletRequest request, RequestContext context) {
        request.setAttribute(REQUEST_ATTRIBUTE, context);
    }

    public RequestContext withDefaultActor(RequestContext context, String actorType, String actorId) {
        return requestContextSupport.withDefaultActor(context, actorType, actorId);
    }

    public void writeResponseHeaders(java.util.function.BiConsumer<String, String> headerWriter, RequestContext context) {
        requestContextSupport.writeResponseHeaders(headerWriter, context);
    }
}
