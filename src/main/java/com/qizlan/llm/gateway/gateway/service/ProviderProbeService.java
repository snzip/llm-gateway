package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.gateway.provider.ProviderAdapter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ProviderProbeService {

    private final List<ProviderAdapter> providerAdapters;
    private final ProviderHealthService providerHealthService;
    private final GatewayProperties properties;

    public ProviderProbeService(List<ProviderAdapter> providerAdapters, ProviderHealthService providerHealthService, GatewayProperties properties) {
        this.providerAdapters = providerAdapters;
        this.providerHealthService = providerHealthService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${llm.gateway.probe.fixed-delay-millis:300000}")
    public void scheduledProbe() {
        if (properties.probe() == null || !properties.probe().enabled()) {
            return;
        }
        probeAll();
    }

    public Map<String, Object> probeAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (ProviderAdapter adapter : providerAdapters) {
            if ("mock".equals(adapter.providerId()) || !isEnabled(adapter.providerId())) {
                continue;
            }
            result.put(adapter.providerId(), probeProvider(adapter.providerId()));
        }
        return result;
    }

    public Map<String, Object> probeProvider(String providerId) {
        ProviderAdapter adapter = providerAdapters.stream()
                .filter(candidate -> candidate.providerId().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + providerId));
        try {
            int models = adapter.listModels().size();
            providerHealthService.recordSuccess(providerId);
            return Map.of("healthy", true, "provider_id", providerId, "discovered_models", models);
        } catch (RuntimeException ex) {
            providerHealthService.recordFailure(providerId, ex.getMessage());
            return Map.of("healthy", false, "provider_id", providerId, "error", ex.getMessage());
        }
    }

    private boolean isEnabled(String providerId) {
        return switch (providerId) {
            case "openai" -> properties.providers().openai().enabled();
            case "anthropic" -> properties.providers().anthropic().enabled();
            case "google" -> properties.providers().google().enabled();
            default -> false;
        };
    }
}
