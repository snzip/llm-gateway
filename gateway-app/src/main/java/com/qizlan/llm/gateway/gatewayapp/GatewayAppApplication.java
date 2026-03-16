package com.qizlan.llm.gateway.gatewayapp;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.qizlan.llm.gateway")
@EntityScan(basePackages = "com.qizlan.llm.gateway.persistence.entity")
@EnableJpaRepositories(basePackages = "com.qizlan.llm.gateway.persistence.repository")
@EnableScheduling
public class GatewayAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayAppApplication.class, args);
    }
}
