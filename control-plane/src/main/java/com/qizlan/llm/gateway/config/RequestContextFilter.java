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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var context = requestContextService.create(
                request.getHeader(RequestContextService.CORRELATION_ID_HEADER),
                request.getHeader(RequestContextService.ACTOR_TYPE_HEADER),
                request.getHeader(RequestContextService.ACTOR_ID_HEADER)
        );
        requestContextService.set(request, context);
        requestContextService.writeResponseHeaders(response::setHeader, context);
        filterChain.doFilter(request, response);
    }
}
