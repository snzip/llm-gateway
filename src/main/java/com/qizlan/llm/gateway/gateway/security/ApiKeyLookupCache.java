package com.qizlan.llm.gateway.gateway.security;

import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.repository.ApiKeyRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyLookupCache {

    private final ApiKeyRepository apiKeyRepository;
    private final Map<String, Optional<ApiKeyEntity>> byTokenHash = new ConcurrentHashMap<>();

    public ApiKeyLookupCache(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    public Optional<ApiKeyEntity> findActiveByTokenHash(String tokenHash) {
        return byTokenHash.computeIfAbsent(tokenHash, apiKeyRepository::findByTokenHashAndActiveTrue);
    }

    public void evict(ApiKeyEntity apiKey) {
        if (apiKey != null && apiKey.getTokenHash() != null) {
            byTokenHash.remove(apiKey.getTokenHash());
        }
    }

    public void clear() {
        byTokenHash.clear();
    }
}
