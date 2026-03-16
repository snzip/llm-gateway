package com.qizlan.llm.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LLM Gateway API")
                        .version("1.0.1")
                        .description("统一的 WebFlux LLM 网关 + 控制面")
                        .contact(new Contact().name("Qizlan Team").email("infra@qizlan.com")))
                .servers(List.of(new Server().url("/")))
                .tags(List.of(
                        new Tag().name("Data Plane").description("LLM data plane endpoints for routing and streaming."),
                        new Tag().name("Control Plane").description("Administrative APIs for IAM, cost, and log control."),
                        new Tag().name("MCP").description("Multi-cluster provisioning capabilities.")
                ))
                .components(new Components().addSecuritySchemes("BearerAuth",
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }
}
