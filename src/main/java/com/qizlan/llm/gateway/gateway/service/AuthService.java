package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.SessionEntity;
import com.qizlan.llm.gateway.persistence.entity.UserEntity;
import com.qizlan.llm.gateway.persistence.repository.UserRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, SessionService sessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    public Map<String, Object> login(String email, String password) {
        UserEntity user = userRepository.findByEmail(email)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        SessionService.CreatedSession created = sessionService.createSession(user, Duration.ofDays(7));
        Duration ttl = Duration.between(OffsetDateTime.now(), created.session().getExpiresAt());
        return Map.of(
                "access_token", created.rawToken(),
                "token_type", "Bearer",
                "expires_in", ttl.getSeconds()
        );
    }

    public void logout(String token) {
        sessionService.validate(token).ifPresent(sessionService::revoke);
    }

    public Map<String, Object> verifyToken(String token) {
        var session = sessionService.validate(token);
        if (session.isEmpty()) {
            return Map.of();
        }
        return Map.of("expires_at", session.get().getExpiresAt().toString());
    }
}
