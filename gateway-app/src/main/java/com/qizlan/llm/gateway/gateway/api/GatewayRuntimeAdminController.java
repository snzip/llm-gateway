package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.service.ModelSyncService;
import com.qizlan.llm.gateway.gateway.service.ProviderProbeService;
import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(
        name = "Gateway Runtime Admin",
        description = "Gateway runtime maintenance endpoints for provider probing and model synchronization")
public class GatewayRuntimeAdminController {

    private final ModelSyncService modelSyncService;
    private final ProviderProbeService providerProbeService;
    private final Scheduler controlPlaneScheduler;

    public GatewayRuntimeAdminController(
            ModelSyncService modelSyncService,
            ProviderProbeService providerProbeService,
            Scheduler controlPlaneScheduler
    ) {
        this.modelSyncService = modelSyncService;
        this.providerProbeService = providerProbeService;
        this.controlPlaneScheduler = controlPlaneScheduler;
    }

    @PostMapping("/internal/models/sync")
    public Mono<Map<String, Object>> syncModels(@RequestParam(name = "provider", required = false) String provider) {
        return blocking(() -> {
            if (provider == null || provider.isBlank()) {
                return modelSyncService.syncAll();
            }
            return Map.of("synced_mappings", modelSyncService.syncProvider(provider));
        });
    }

    @GetMapping("/internal/models/sync/history")
    public Mono<Map<String, Object>> syncHistory(@RequestParam(name = "provider", required = false) String provider) {
        return blocking(() -> Map.of("data", modelSyncService.history(provider).stream().map(history -> Map.of(
                "id", history.getId(),
                "provider_id", history.getProviderId(),
                "status", history.getStatus(),
                "discovered_models", history.getDiscoveredModels(),
                "synced_mappings", history.getSyncedMappings(),
                "archived_mappings", history.getArchivedMappings(),
                "detail", history.getDetail(),
                "created_at", history.getCreatedAt()
        )).toList()));
    }

    @PostMapping("/internal/providers/probe")
    public Mono<Map<String, Object>> probeProviders(@RequestParam(name = "provider", required = false) String provider) {
        return blocking(() -> {
            if (provider == null || provider.isBlank()) {
                return Map.of("data", providerProbeService.probeAll());
            }
            return Map.of("data", providerProbeService.probeProvider(provider));
        });
    }

    @GetMapping("/internal/providers/probe/history")
    public Mono<Map<String, Object>> probeHistory(@RequestParam(name = "provider", required = false) String provider) {
        return blocking(() -> Map.of("data", providerProbeService.history(provider).stream().map(item -> Map.of(
                "id", item.getId(),
                "provider_id", item.getProviderId(),
                "healthy", item.isHealthy(),
                "latency_ms", item.getLatencyMs(),
                "probe_model", item.getProbeModel(),
                "strategy", item.getStrategy(),
                "error_message", item.getErrorMessage() == null ? "" : item.getErrorMessage(),
                "created_at", item.getCreatedAt().toString()
        )).toList()));
    }

    private <T> Mono<T> blocking(Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(controlPlaneScheduler);
    }
}
