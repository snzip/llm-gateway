package com.qizlan.llm.gateway.gateway.security;

import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.repository.ApiKeyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private final ApiKeyRepository apiKeyRepository;
    private final GatewayProperties properties;
    private final ApiKeyTokenService tokenService;
    private final IamRuleService iamRuleService;
    private final ApiKeyRateLimitService apiKeyRateLimitService;

    public ApiKeyAuthInterceptor(
            ApiKeyRepository apiKeyRepository,
            GatewayProperties properties,
            ApiKeyTokenService tokenService,
            IamRuleService iamRuleService,
            ApiKeyRateLimitService apiKeyRateLimitService
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.properties = properties;
        this.tokenService = tokenService;
        this.iamRuleService = iamRuleService;
        this.apiKeyRateLimitService = apiKeyRateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String headerValue = request.getHeader(properties.apiKeyHeader());
        if (headerValue == null || !headerValue.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Bearer token");
            return false;
        }

        String token = headerValue.substring("Bearer ".length()).trim();
        ApiKeyEntity apiKey = apiKeyRepository.findByTokenHashAndActiveTrue(tokenService.hash(token)).orElse(null);
        if (apiKey == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return false;
        }
        try {
            iamRuleService.assertBudgetAllowed(apiKey);
            iamRuleService.assertPathAllowed(apiKey, request.getRequestURI());
            int dynamicRateLimit = iamRuleService.resolveRateLimit(apiKey);
            if (dynamicRateLimit > 0) {
                apiKey.setRequestsPerMinuteLimit(dynamicRateLimit);
            }
            apiKeyRateLimitService.assertAllowed(apiKey);
        } catch (ApiKeyAccessDeniedException ex) {
            int status = ex.getMessage().contains("rate limit") ? 429 : HttpServletResponse.SC_FORBIDDEN;
            response.sendError(status, ex.getMessage());
            return false;
        }
        request.setAttribute("apiKey", apiKey);
        return true;
    }
}
