package com.qizlan.llm.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class PropertiesConfig {
}
