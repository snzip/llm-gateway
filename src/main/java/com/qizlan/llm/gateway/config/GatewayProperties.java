package com.qizlan.llm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.gateway")
public record GatewayProperties(
        String apiKeyHeader,
        SeedProperties seed,
        ProviderProperties providers,
        RoutingProperties routing,
        SyncProperties sync,
        AggregationProperties aggregation,
        ProbeProperties probe
) {
    public record SeedProperties(
            boolean enabled,
            String apiKey,
            String name
    ) {
    }

    public record ProviderProperties(
            String mode,
            Endpoint openai,
            Endpoint anthropic,
            Endpoint google
    ) {
    }

    public record Endpoint(
            boolean enabled,
            String baseUrl,
            String apiKey
    ) {
    }

    public record RoutingProperties(
            int failureThreshold,
            long cooldownMillis
    ) {
    }

    public record SyncProperties(
            boolean enabled,
            long fixedDelayMillis,
            String conflictPolicy
    ) {
    }

    public record AggregationProperties(
            boolean enabled,
            long fixedDelayMillis,
            long cacheTtlMillis,
            long retentionDays
    ) {
    }

    public record ProbeProperties(
            boolean enabled,
            long fixedDelayMillis
    ) {
    }
}
