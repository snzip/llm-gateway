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
        ProbeProperties probe,
        RequestLogProperties requestLog,
        McpProperties mcp
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
            long cooldownMillis,
            long projectionRefreshCheckMillis
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

    public record RequestLogProperties(
            boolean payloadSamplingEnabled,
            double sampleRate,
            int maxBodyChars,
            boolean storeResponsePayload,
            java.util.List<String> redactedFields
    ) {
    }

    public record McpProperties(
            java.util.Map<String, String> toolScopes
    ) {
    }
}
