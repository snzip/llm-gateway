package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.service.ProviderHealthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final ProviderHealthService providerHealthService;

    public HealthController(ProviderHealthService providerHealthService) {
        this.providerHealthService = providerHealthService;
    }

    @GetMapping("/")
    public Map<String, Object> health() {
        return Map.of(
                "message", "LLM Gateway Spring Boot",
                "version", "0.0.1-SNAPSHOT",
                "health", Map.of(
                        "status", "UP",
                        "database", Map.of("connected", true),
                        "redis", Map.of("connected", false, "error", "Not configured"),
                        "providers", providerHealthService.snapshot()
                )
        );
    }

    @GetMapping("/internal/providers/health")
    public Map<String, Object> providerHealth() {
        return Map.of("data", providerHealthService.snapshot());
    }
}
