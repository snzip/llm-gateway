package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.SessionEntity;
import com.qizlan.llm.gateway.persistence.entity.UserEntity;
import com.qizlan.llm.gateway.persistence.repository.SessionRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

@Service
public class SessionService {

    public static final String SESSION_ATTRIBUTE = SessionService.class.getName() + ".session";
    public static final String USER_ATTRIBUTE = SessionService.class.getName() + ".user";

    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionService(SessionRepository sessionRepository, PasswordEncoder passwordEncoder) {
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public CreatedSession createSession(UserEntity user, Duration ttl) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String prefix = token.substring(0, Math.min(8, token.length()));
        SessionEntity session = new SessionEntity();
        session.setTokenId(UUID.randomUUID().toString().replace("-", ""));
        session.setTokenHash(passwordEncoder.encode(token));
        session.setTokenPrefix(prefix);
        session.setExpiresAt(OffsetDateTime.now().plus(ttl));
        session.setRevoked(false);
        session.setUser(user);
        return new CreatedSession(sessionRepository.save(session), token);
    }

    public Optional<SessionEntity> validate(String token) {
        String prefix = token.length() >= 8 ? token.substring(0, 8) : token;
        List<SessionEntity> candidates = sessionRepository.findByTokenPrefix(prefix);
        OffsetDateTime now = OffsetDateTime.now();
        for (SessionEntity candidate : candidates) {
            if (candidate.isRevoked() || candidate.getExpiresAt().isBefore(now)) {
                continue;
            }
            if (passwordEncoder.matches(token, candidate.getTokenHash())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public void revoke(SessionEntity session) {
        session.setRevoked(true);
        sessionRepository.save(session);
    }

    public void bind(ServerWebExchange exchange, SessionEntity session) {
        exchange.getAttributes().put(SESSION_ATTRIBUTE, session);
        exchange.getAttributes().put(USER_ATTRIBUTE, session.getUser());
    }

    public Optional<UserEntity> currentUser(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getAttribute(USER_ATTRIBUTE));
    }

    public record CreatedSession(SessionEntity session, String rawToken) {
    }
}
