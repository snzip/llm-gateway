package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import com.qizlan.llm.gateway.persistence.repository.ModelProviderMappingRepository;
import java.util.Comparator;
import java.util.List;

public class RoutingService {

    private final ModelProviderMappingRepository mappingRepository;
    private final ProviderAvailabilityPolicy providerAvailabilityPolicy;
    private final ProviderAccessPolicy providerAccessPolicy;
    private final ModelRoutingCache modelRoutingCache;
    private final RoutingProjectionInvalidationService routingProjectionInvalidationService;

    public RoutingService(
            ModelProviderMappingRepository mappingRepository,
            ProviderAvailabilityPolicy providerAvailabilityPolicy,
            ProviderAccessPolicy providerAccessPolicy,
            ModelRoutingCache modelRoutingCache,
            RoutingProjectionInvalidationService routingProjectionInvalidationService
    ) {
        this.mappingRepository = mappingRepository;
        this.providerAvailabilityPolicy = providerAvailabilityPolicy;
        this.providerAccessPolicy = providerAccessPolicy;
        this.modelRoutingCache = modelRoutingCache;
        this.routingProjectionInvalidationService = routingProjectionInvalidationService;
    }

    public ModelProviderMappingEntity resolve(String requestedModel, ApiKeyEntity apiKey) {
        return resolveCandidates(requestedModel, apiKey).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown model: " + requestedModel));
    }

    public List<ModelProviderMappingEntity> resolveCandidates(String requestedModel, ApiKeyEntity apiKey) {
        routingProjectionInvalidationService.refreshIfNeeded();
        if (requestedModel.contains("/")) {
            String[] parts = requestedModel.split("/", 2);
            String providerId = parts[0];
            String modelId = parts[1];
            return providerAccessPolicy.filterAllowedProviders(apiKey, mappingRepository.findAll().stream()
                    .filter(m -> m.isActive() && !m.getModel().isArchived())
                    .filter(m -> m.getProvider().getId().equals(providerId) && m.getModel().getId().equals(modelId))
                    .toList());
        }
        List<ModelProviderMappingEntity> mappings = modelRoutingCache.get(requestedModel, () -> mappingRepository.findByModelIdOrderByPriorityAscProviderIdAsc(requestedModel).stream()
                .filter(ModelProviderMappingEntity::isActive)
                .filter(mapping -> !mapping.getModel().isArchived())
                .toList());
        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("Unknown model: " + requestedModel);
        }
        return providerAccessPolicy.filterAllowedProviders(apiKey, mappings.stream()
                .sorted(Comparator
                        .comparing((ModelProviderMappingEntity mapping) -> !providerAvailabilityPolicy.isAvailable(mapping.getProvider().getId()))
                        .thenComparingInt(ModelProviderMappingEntity::getPriority)
                        .thenComparing(mapping -> mapping.getProvider().getId()))
                .toList());
    }

    public void evictModel(String modelId) {
        modelRoutingCache.evict(modelId);
    }
}
