package com.qizlan.llm.gateway.gateway.security;

import com.qizlan.llm.gateway.config.GatewayProperties;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyPathPolicy {

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/",
            "/user",
            "/metrics",
            "/docs",
            "/json",
            "/.well-known",
            "/oauth",
            "/auth",
            "/mcp"
    );

    private final GatewayProperties properties;

    public ApiKeyPathPolicy(GatewayProperties properties) {
        this.properties = properties;
    }

    public boolean requiresApiKey(String path) {
        if ("/v1/models".equals(path)) {
            return false;
        }
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return false;
            }
        }
        return path.startsWith("/v1/") || path.startsWith("/orgs") || path.startsWith("/projects")
                || path.startsWith("/keys") || path.startsWith("/logs") || path.startsWith("/costs")
                || path.startsWith("/guardrails") || path.startsWith("/audit-logs")
                || path.startsWith("/admin") || path.startsWith("/team") || path.startsWith("/payments")
                || path.startsWith("/subscriptions") || path.startsWith("/dev-plans") || path.startsWith("/beacon")
                || path.startsWith("/referral") || path.startsWith("/public");
    }

    public String apiKeyHeader() {
        return properties.apiKeyHeader();
    }
}
