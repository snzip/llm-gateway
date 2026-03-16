package com.qizlan.llm.gateway.controlplane;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.qizlan.llm.gateway")
@EntityScan(basePackages = "com.qizlan.llm.gateway.persistence.entity")
@EnableJpaRepositories(basePackages = "com.qizlan.llm.gateway.persistence.repository")
public class ControlPlaneApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlPlaneApplication.class, args);
    }
}
