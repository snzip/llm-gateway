package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.gateway.dto.ChatCompletionRequest;
import com.qizlan.llm.gateway.gateway.provider.ProviderAdapter;
import com.qizlan.llm.gateway.gateway.provider.ProviderChatResult;
import com.qizlan.llm.gateway.gateway.provider.ProviderModelDescriptor;
import com.qizlan.llm.gateway.persistence.entity.ProviderProbeHistoryEntity;
import com.qizlan.llm.gateway.persistence.repository.ProviderProbeHistoryRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

@Service
public class ProviderProbeService {

    private final List<ProviderAdapter> providerAdapters;
    private final ProviderHealthService providerHealthService;
    private final ProviderProbeHistoryRepository providerProbeHistoryRepository;
    private final GatewayProperties properties;

    public ProviderProbeService(List<ProviderAdapter> providerAdapters, ProviderHealthService providerHealthService, ProviderProbeHistoryRepository providerProbeHistoryRepository, GatewayProperties properties) {
        this.providerAdapters = providerAdapters;
        this.providerHealthService = providerHealthService;
        this.providerProbeHistoryRepository = providerProbeHistoryRepository;
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
        long startedAt = System.currentTimeMillis();
        String probeModel = "unknown";
        try {
            List<String> probeCandidates = adapter.listModels().stream()
                    .filter(model -> !model.imageGeneration())
                    .sorted(Comparator.comparingInt(ProviderModelDescriptor::priority))
                    .map(ProviderModelDescriptor::providerModelName)
                    .toList();
            probeModel = probeCandidates.isEmpty() ? "unknown" : probeCandidates.get(0);
            int models = probeCandidates.size();
            if (!probeCandidates.isEmpty()) {
                ProviderChatResult result = adapter.complete(new ChatCompletionRequest(
                        probeModel,
                        List.of(new ChatCompletionRequest.ChatMessageInput("user", "ping", null, null, null)),
                        0.0, 8, null, null, null, null, false,
                        null, null, null, null, null, null, null, null, null, null, null, null
                ), probeModel);
                if (result.content() == null) {
                    throw new IllegalStateException("Empty probe response");
                }
            }
            providerHealthService.recordSuccess(providerId);
            long latency = System.currentTimeMillis() - startedAt;
            providerProbeHistoryRepository.save(new ProviderProbeHistoryEntity(providerId, true, latency, probeModel, "synthetic_completion", null));
            return Map.of("healthy", true, "provider_id", providerId, "discovered_models", models, "probe_model", probeModel, "latency_ms", latency, "strategy", "synthetic_completion");
        } catch (RuntimeException ex) {
            providerHealthService.recordFailure(providerId, ex.getMessage());
            long latency = System.currentTimeMillis() - startedAt;
            providerProbeHistoryRepository.save(new ProviderProbeHistoryEntity(providerId, false, latency, probeModel, "synthetic_completion", ex.getMessage()));
            return Map.of("healthy", false, "provider_id", providerId, "probe_model", probeModel, "latency_ms", latency, "strategy", "synthetic_completion", "error", ex.getMessage());
        }
    }

    public List<ProviderProbeHistoryEntity> history(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return providerProbeHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return providerProbeHistoryRepository.findByProviderId(providerId, Sort.by(Sort.Direction.DESC, "createdAt"));
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
