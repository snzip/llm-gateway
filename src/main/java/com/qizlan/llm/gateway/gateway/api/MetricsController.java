package com.qizlan.llm.gateway.gateway.api;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {

    private final ObjectProvider<PrometheusMeterRegistry> meterRegistryProvider;
    private final MeterRegistry meterRegistry;

    public MetricsController(ObjectProvider<PrometheusMeterRegistry> meterRegistryProvider, MeterRegistry meterRegistry) {
        this.meterRegistryProvider = meterRegistryProvider;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        PrometheusMeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            return meterRegistry.scrape();
        }
        String exportedMeters = this.meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().getName() + tags(meterRegistry, meter))
                .distinct()
                .sorted()
                .reduce("", (left, right) -> left + right + "\n");
        if (!exportedMeters.isBlank()) {
            return exportedMeters;
        }
        return "llmgateway_up 1\n";
    }

    private String tags(PrometheusMeterRegistry ignored, io.micrometer.core.instrument.Meter meter) {
        if (meter.getId().getTags().isEmpty()) {
            return "";
        }
        return meter.getId().getTags().stream()
                .map(tag -> tag.getKey() + "=\"" + tag.getValue() + "\"")
                .reduce("{", (left, right) -> "{".equals(left) ? left + right : left + "," + right) + "}";
    }
}
