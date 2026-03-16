package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.config.GatewayProperties;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import com.qizlan.llm.gateway.gateway.security.ApiKeyPathPolicy;

@TestConfiguration
public class TestGatewayConfig {

    @Bean
    public GatewayProperties gatewayProperties() {
        GatewayProperties.Endpoint endpoint = new GatewayProperties.Endpoint(false, "", "");
        return new GatewayProperties(
                "Authorization",
                new GatewayProperties.SeedProperties(true, "test-api-key", "Test seed key"),
                new GatewayProperties.ProviderProperties("single", endpoint, endpoint, endpoint),
                new GatewayProperties.RoutingProperties(3, 1000L, 0L),
                new GatewayProperties.SyncProperties(false, 0L, ""),
                new GatewayProperties.AggregationProperties(false, 0L, 0L, 0L),
                new GatewayProperties.ProbeProperties(false, 0L),
                new GatewayProperties.RequestLogProperties(false, 0.0, 0, false, List.of()),
                new GatewayProperties.McpProperties(Map.of())
        );
    }

    @Bean
    public Scheduler controlPlaneScheduler() {
        return Schedulers.single();
    }

    @Bean
    public ApiKeyPathPolicy apiKeyPathPolicy(GatewayProperties gatewayProperties) {
        return new ApiKeyPathPolicy(gatewayProperties);
    }
}
