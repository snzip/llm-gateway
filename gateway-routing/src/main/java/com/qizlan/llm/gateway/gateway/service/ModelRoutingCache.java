package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ModelRoutingCache {

    private final Map<String, List<ModelProviderMappingEntity>> byModel = new ConcurrentHashMap<>();

    public List<ModelProviderMappingEntity> get(String modelId, Supplier<List<ModelProviderMappingEntity>> loader) {
        return byModel.computeIfAbsent(modelId, ignored -> loader.get());
    }

    public void evict(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            byModel.remove(modelId);
        }
    }

    public void clear() {
        byModel.clear();
    }
}
