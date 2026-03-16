package com.qizlan.llm.gateway.gateway.service;

public class RoutingProjectionInvalidationService {

    private final RoutingProjectionStateService routingProjectionStateService;
    private final ModelRoutingCache modelRoutingCache;
    private final long checkIntervalMillis;

    private volatile long lastSeenVersion;
    private volatile long lastCheckedAtMillis;

    public RoutingProjectionInvalidationService(
            RoutingProjectionStateService routingProjectionStateService,
            ModelRoutingCache modelRoutingCache,
            long checkIntervalMillis
    ) {
        this.routingProjectionStateService = routingProjectionStateService;
        this.modelRoutingCache = modelRoutingCache;
        this.checkIntervalMillis = Math.max(0L, checkIntervalMillis);
        this.lastSeenVersion = routingProjectionStateService.currentVersion().version();
        this.lastCheckedAtMillis = 0L;
    }

    public void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCheckedAtMillis < checkIntervalMillis) {
            return;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - lastCheckedAtMillis < checkIntervalMillis) {
                return;
            }
            RoutingProjectionStateService.ProjectionVersion remote = routingProjectionStateService.currentVersion();
            if (remote.version() > lastSeenVersion) {
                modelRoutingCache.clear();
                lastSeenVersion = remote.version();
            }
            lastCheckedAtMillis = now;
        }
    }
}
