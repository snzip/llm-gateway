package com.qizlan.llm.gateway.gateway.security;

import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyIamRuleEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import com.qizlan.llm.gateway.persistence.repository.ApiKeyIamRuleRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

@Service
public class IamRuleService {

    private final ApiKeyIamRuleRepository apiKeyIamRuleRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, List<ApiKeyIamRuleEntity>> ruleCache = new ConcurrentHashMap<>();

    public IamRuleService(ApiKeyIamRuleRepository apiKeyIamRuleRepository) {
        this.apiKeyIamRuleRepository = apiKeyIamRuleRepository;
    }

    public void assertBudgetAllowed(ApiKeyEntity apiKey) {
        if (apiKey == null) {
            return;
        }
        if (apiKey.getBudgetMicrosUsd() > 0 && apiKey.getSpentMicrosUsd() >= apiKey.getBudgetMicrosUsd()) {
            throw new ApiKeyAccessDeniedException("API key budget exceeded");
        }
    }

    public void assertPathAllowed(ApiKeyEntity apiKey, String path) {
        assertAllowed(loadRules(apiKey, "PATH"), path, "Path access denied: " + path, true);
    }

    public void assertModelAllowed(ApiKeyEntity apiKey, String model) {
        assertAllowed(loadRules(apiKey, "MODEL"), model, "Model access denied: " + model, false);
    }

    public List<ModelProviderMappingEntity> filterAllowedProviders(ApiKeyEntity apiKey, List<ModelProviderMappingEntity> candidates) {
        List<ApiKeyIamRuleEntity> rules = loadRules(apiKey, "PROVIDER");
        return candidates.stream()
                .filter(candidate -> isAllowed(rules, candidate.getProvider().getId(), false))
                .toList();
    }

    public RateLimitPolicy resolveRateLimit(ApiKeyEntity apiKey) {
        List<ApiKeyIamRuleEntity> rules = loadRules(apiKey, "RATE");
        if (rules.isEmpty()) {
            int fallback = apiKey == null ? 0 : apiKey.getRequestsPerMinuteLimit();
            return fallback <= 0 ? RateLimitPolicy.disabled() : new RateLimitPolicy(fallback, 60, Math.max(1, fallback));
        }
        return rules.stream()
                .map(ApiKeyIamRuleEntity::getPattern)
                .map(this::parseRateLimitPolicy)
                .filter(policy -> policy.requestLimit() > 0)
                .findFirst()
                .orElse(RateLimitPolicy.disabled());
    }

    public List<ApiKeyIamRuleEntity> listRules(String apiKeyId) {
        return apiKeyIamRuleRepository.findByApiKeyIdOrderByRuleTypeAscEffectAscPatternAsc(apiKeyId);
    }

    private List<ApiKeyIamRuleEntity> loadRules(ApiKeyEntity apiKey, String type) {
        if (apiKey == null) {
            return List.of();
        }
        return ruleCache.computeIfAbsent(apiKey.getId(), apiKeyIamRuleRepository::findByApiKeyIdOrderByRuleTypeAscEffectAscPatternAsc).stream()
                .filter(ApiKeyIamRuleEntity::isActive)
                .filter(rule -> type.equalsIgnoreCase(rule.getRuleType()))
                .toList();
    }

    public void evictRules(String apiKeyId) {
        if (apiKeyId != null && !apiKeyId.isBlank()) {
            ruleCache.remove(apiKeyId);
        }
    }

    private void assertAllowed(List<ApiKeyIamRuleEntity> rules, String value, String errorMessage, boolean path) {
        if (!isAllowed(rules, value, path)) {
            throw new ApiKeyAccessDeniedException(errorMessage);
        }
    }

    private boolean isAllowed(List<ApiKeyIamRuleEntity> rules, String value, boolean path) {
        List<ApiKeyIamRuleEntity> denyRules = rules.stream()
                .filter(rule -> "DENY".equalsIgnoreCase(rule.getEffect()))
                .toList();
        for (ApiKeyIamRuleEntity rule : denyRules) {
            if (matches(rule.getPattern(), value, path)) {
                return false;
            }
        }
        List<ApiKeyIamRuleEntity> allowRules = rules.stream()
                .filter(rule -> "ALLOW".equalsIgnoreCase(rule.getEffect()))
                .toList();
        if (allowRules.isEmpty()) {
            return true;
        }
        return allowRules.stream().anyMatch(rule -> matches(rule.getPattern(), value, path));
    }

    private boolean matches(String pattern, String value, boolean path) {
        if (path) {
            return pathMatcher.match(pattern, value);
        }
        if ("*".equals(pattern)) {
            return true;
        }
        if (pattern.endsWith("*")) {
            return value.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equalsIgnoreCase(value);
    }

    private RateLimitPolicy parseRateLimitPolicy(String value) {
        if (value == null || value.isBlank()) {
            return RateLimitPolicy.disabled();
        }
        String[] parts = value.split("/");
        int requestLimit = safeParseInt(parts[0]);
        int windowSeconds = parts.length >= 2 ? safeParseInt(parts[1]) : 60;
        int burstLimit = parts.length >= 3 ? safeParseInt(parts[2]) : requestLimit;
        if (requestLimit <= 0) {
            return RateLimitPolicy.disabled();
        }
        return new RateLimitPolicy(requestLimit, Math.max(1, windowSeconds), Math.max(1, burstLimit));
    }

    private int safeParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public record RateLimitPolicy(int requestLimit, int windowSeconds, int burstLimit) {
        public static RateLimitPolicy disabled() {
            return new RateLimitPolicy(0, 60, 0);
        }
    }
}
