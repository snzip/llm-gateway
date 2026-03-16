package com.qizlan.llm.gateway.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class GatewayConfigurationValidator implements ApplicationRunner {

    private final GatewayProperties properties;
    private final Environment environment;

    public GatewayConfigurationValidator(GatewayProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = hasProfile("prod");
        boolean test = hasProfile("test");

        if (prod && properties.seed() != null && properties.seed().enabled()) {
            throw new IllegalStateException("Seed API key must be disabled in prod profile");
        }

        if (!test && properties.providers() != null && "real".equalsIgnoreCase(properties.providers().mode())) {
            validateProvider("openai", properties.providers().openai());
            validateProvider("anthropic", properties.providers().anthropic());
            validateProvider("google", properties.providers().google());
        }
    }

    private void validateProvider(String providerId, GatewayProperties.Endpoint endpoint) {
        if (endpoint == null || !endpoint.enabled()) {
            return;
        }
        if (endpoint.apiKey() == null || endpoint.apiKey().isBlank()) {
            throw new IllegalStateException("Provider api key is required when provider is enabled: " + providerId);
        }
    }

    private boolean hasProfile(String expected) {
        for (String profile : environment.getActiveProfiles()) {
            if (expected.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
