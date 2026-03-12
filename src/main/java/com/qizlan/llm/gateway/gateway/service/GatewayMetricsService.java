package com.qizlan.llm.gateway.gateway.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class GatewayMetricsService {

    private final MeterRegistry meterRegistry;

    public GatewayMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordGatewayRequest(
            String path,
            String providerId,
            String model,
            int httpStatus,
            long latencyMs,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long estimatedCostMicrosUsd,
            boolean streamed
    ) {
        String provider = normalize(providerId);
        String normalizedModel = normalize(model);
        String statusFamily = statusFamily(httpStatus);
        String streamTag = Boolean.toString(streamed);

        Counter.builder("llm.gateway.provider.requests")
                .tag("path", normalize(path))
                .tag("provider", provider)
                .tag("model", normalizedModel)
                .tag("status_family", statusFamily)
                .tag("streamed", streamTag)
                .register(meterRegistry)
                .increment();

        Timer.builder("llm.gateway.provider.latency")
                .tag("path", normalize(path))
                .tag("provider", provider)
                .tag("model", normalizedModel)
                .tag("status_family", statusFamily)
                .register(meterRegistry)
                .record(Math.max(latencyMs, 0L), TimeUnit.MILLISECONDS);

        recordTokenSummary(provider, normalizedModel, "prompt", promptTokens);
        recordTokenSummary(provider, normalizedModel, "completion", completionTokens);
        recordTokenSummary(provider, normalizedModel, "total", totalTokens);

        DistributionSummary.builder("llm.gateway.provider.cost.micros")
                .baseUnit("micros_usd")
                .tag("path", normalize(path))
                .tag("provider", provider)
                .tag("model", normalizedModel)
                .register(meterRegistry)
                .record(Math.max(estimatedCostMicrosUsd, 0L));
    }

    public void recordProviderProbe(String providerId, boolean healthy, long latencyMs) {
        Counter.builder("llm.gateway.provider.probes")
                .tag("provider", normalize(providerId))
                .tag("healthy", Boolean.toString(healthy))
                .register(meterRegistry)
                .increment();

        Timer.builder("llm.gateway.provider.probe.latency")
                .tag("provider", normalize(providerId))
                .tag("healthy", Boolean.toString(healthy))
                .register(meterRegistry)
                .record(Math.max(latencyMs, 0L), TimeUnit.MILLISECONDS);
    }

    public void recordHttpExchange(String method, String path, int status, long latencyMs) {
        Counter.builder("llm.gateway.http.requests")
                .tag("method", normalize(method))
                .tag("path", normalize(path))
                .tag("status_family", statusFamily(status))
                .register(meterRegistry)
                .increment();

        Timer.builder("llm.gateway.http.latency")
                .tag("method", normalize(method))
                .tag("path", normalize(path))
                .tag("status_family", statusFamily(status))
                .register(meterRegistry)
                .record(Math.max(latencyMs, 0L), TimeUnit.MILLISECONDS);
    }

    private void recordTokenSummary(String provider, String model, String tokenType, int value) {
        DistributionSummary.builder("llm.gateway.provider.tokens")
                .baseUnit("tokens")
                .tag("provider", provider)
                .tag("model", model)
                .tag("type", tokenType)
                .register(meterRegistry)
                .record(Math.max(value, 0));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String statusFamily(int status) {
        if (status < 200) {
            return "1xx";
        }
        if (status < 300) {
            return "2xx";
        }
        if (status < 400) {
            return "3xx";
        }
        if (status < 500) {
            return "4xx";
        }
        return "5xx";
    }
}
