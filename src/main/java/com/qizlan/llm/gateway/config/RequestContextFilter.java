package com.qizlan.llm.gateway.config;

import com.qizlan.llm.gateway.gateway.service.RequestContextService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("gatewayRequestContextFilter")
public class RequestContextFilter extends OncePerRequestFilter {

    private final RequestContextService requestContextService;

    public RequestContextFilter(RequestContextService requestContextService) {
        this.requestContextService = requestContextService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        requestContextService.set(
                request.getHeader("X-Correlation-Id"),
                request.getHeader("X-Actor-Type"),
                request.getHeader("X-Actor-Id")
        );
        response.setHeader("X-Correlation-Id", requestContextService.get().correlationId());
        try {
            filterChain.doFilter(request, response);
        } finally {
            requestContextService.clear();
        }
    }
}
