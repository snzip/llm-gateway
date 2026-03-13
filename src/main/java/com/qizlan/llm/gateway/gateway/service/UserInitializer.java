package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.config.AuthProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class UserInitializer {

    private final UserService userService;
    private final AuthProperties authProperties;

    public UserInitializer(UserService userService, AuthProperties authProperties) {
        this.userService = userService;
        this.authProperties = authProperties;
    }

    @PostConstruct
    public void ensureSeedUser() {
        userService.findByEmail(authProperties.getSeedUserEmail()).orElseGet(() ->
                userService.create(
                        authProperties.getSeedUserEmail(),
                        authProperties.getSeedUserName(),
                        authProperties.getSeedUserPassword()
                ));
    }
}
