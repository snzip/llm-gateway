package com.qizlan.llm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "llm.gateway.auth")
public class AuthProperties {

    private String seedUserEmail = "admin@example.com";
    private String seedUserPassword = "password";
    private String seedUserName = "Administrator";

    public String getSeedUserEmail() {
        return seedUserEmail;
    }

    public void setSeedUserEmail(String seedUserEmail) {
        this.seedUserEmail = seedUserEmail;
    }

    public String getSeedUserPassword() {
        return seedUserPassword;
    }

    public void setSeedUserPassword(String seedUserPassword) {
        this.seedUserPassword = seedUserPassword;
    }

    public String getSeedUserName() {
        return seedUserName;
    }

    public void setSeedUserName(String seedUserName) {
        this.seedUserName = seedUserName;
    }
}
