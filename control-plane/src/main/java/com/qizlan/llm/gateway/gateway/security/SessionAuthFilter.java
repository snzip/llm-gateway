package com.qizlan.llm.gateway.gateway.security;

import com.qizlan.llm.gateway.gateway.service.SessionService;
import com.qizlan.llm.gateway.persistence.entity.SessionEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    private final SessionService sessionService;

    public SessionAuthFilter(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if ("/auth/login".equals(path)) {
            return true;
        }
        return !requiresSession(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Missing bearer token");
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        SessionEntity session = sessionService.validate(token)
                .orElse(null);
        if (session == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid session token");
            return;
        }
        sessionService.bind(request, session);
        filterChain.doFilter(request, response);
    }

    private boolean requiresSession(String path) {
        return path.startsWith("/auth")
                || path.startsWith("/user")
                || path.startsWith("/admin");
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
