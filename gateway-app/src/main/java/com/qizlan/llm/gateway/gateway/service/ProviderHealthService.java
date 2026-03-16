package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.config.GatewayProperties;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ProviderHealthService {

    private final GatewayProperties properties;
    private final ConcurrentHashMap<String, ProviderHealthState> states = new ConcurrentHashMap<>();

    public ProviderHealthService(GatewayProperties properties) {
        this.properties = properties;
    }

    public boolean isAvailable(String providerId) {
        ProviderHealthState state = states.get(providerId);
        if (state == null) {
            return true;
        }
        if (state.consecutiveFailures() < properties.routing().failureThreshold()) {
            return true;
        }
        long now = Instant.now().toEpochMilli();
        return now - state.lastFailureAtMillis() >= properties.routing().cooldownMillis();
    }

    public void recordSuccess(String providerId) {
        states.put(providerId, new ProviderHealthState(0, 0L, "", true));
    }

    public void recordFailure(String providerId, String error) {
        states.compute(providerId, (key, previous) -> {
            int failures = previous == null ? 1 : previous.consecutiveFailures() + 1;
            return new ProviderHealthState(failures, Instant.now().toEpochMilli(), error == null ? "" : error, false);
        });
    }

    public Map<String, ProviderHealthState> snapshot() {
        return Map.copyOf(states);
    }

    public record ProviderHealthState(
            int consecutiveFailures,
            long lastFailureAtMillis,
            String lastError,
            boolean healthy
    ) {
    }
}
