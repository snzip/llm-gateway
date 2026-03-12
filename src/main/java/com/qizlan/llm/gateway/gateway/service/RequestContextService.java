package com.qizlan.llm.gateway.gateway.service;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RequestContextService {

    private final ThreadLocal<RequestContext> current = new ThreadLocal<>();

    public void set(String correlationId, String actorType, String actorId) {
        current.set(new RequestContext(
                correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : correlationId,
                actorType == null || actorType.isBlank() ? "system" : actorType,
                actorId == null || actorId.isBlank() ? "control-plane" : actorId
        ));
    }

    public RequestContext get() {
        RequestContext context = current.get();
        if (context == null) {
            return new RequestContext(UUID.randomUUID().toString().replace("-", ""), "system", "control-plane");
        }
        return context;
    }

    public void clear() {
        current.remove();
    }

    public void overrideActorIfDefault(String actorType, String actorId) {
        RequestContext context = get();
        if (!"system".equals(context.actorType()) || !"control-plane".equals(context.actorId())) {
            return;
        }
        set(context.correlationId(), actorType, actorId);
    }

    public record RequestContext(String correlationId, String actorType, String actorId) {
    }
}
