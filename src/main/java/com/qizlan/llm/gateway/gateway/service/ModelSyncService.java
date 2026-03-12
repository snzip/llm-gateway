package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.gateway.provider.ProviderAdapter;
import com.qizlan.llm.gateway.gateway.provider.ProviderModelDescriptor;
import com.qizlan.llm.gateway.persistence.entity.ModelEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelSyncHistoryEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import com.qizlan.llm.gateway.persistence.entity.ProviderEntity;
import com.qizlan.llm.gateway.persistence.repository.ModelSyncHistoryRepository;
import com.qizlan.llm.gateway.persistence.repository.ModelProviderMappingRepository;
import com.qizlan.llm.gateway.persistence.repository.ModelRepository;
import com.qizlan.llm.gateway.persistence.repository.ProviderRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

@Service
public class ModelSyncService {

    private final List<ProviderAdapter> providerAdapters;
    private final ProviderRepository providerRepository;
    private final ModelRepository modelRepository;
    private final ModelProviderMappingRepository mappingRepository;
    private final ModelSyncHistoryRepository historyRepository;
    private final GatewayProperties properties;

    public ModelSyncService(
            List<ProviderAdapter> providerAdapters,
            ProviderRepository providerRepository,
            ModelRepository modelRepository,
            ModelProviderMappingRepository mappingRepository,
            ModelSyncHistoryRepository historyRepository,
            GatewayProperties properties
    ) {
        this.providerAdapters = providerAdapters;
        this.providerRepository = providerRepository;
        this.modelRepository = modelRepository;
        this.mappingRepository = mappingRepository;
        this.historyRepository = historyRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${llm.gateway.sync.fixed-delay-millis:3600000}")
    public void scheduledSync() {
        if (!properties.sync().enabled()) {
            return;
        }
        syncAll();
    }

    public Map<String, Object> syncAll() {
        int synced = 0;
        for (ProviderAdapter adapter : providerAdapters) {
            if ("mock".equals(adapter.providerId())) {
                continue;
            }
            synced += syncProvider(adapter.providerId());
        }
        return Map.of("synced_mappings", synced);
    }

    public int syncProvider(String providerId) {
        ProviderAdapter adapter = providerAdapters.stream()
                .filter(candidate -> candidate.providerId().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + providerId));
        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider entity: " + providerId));
        List<ProviderModelDescriptor> descriptors = adapter.listModels();
        Set<String> discoveredModelIds = new HashSet<>();
        int synced = 0;
        for (ProviderModelDescriptor descriptor : descriptors) {
            discoveredModelIds.add(descriptor.gatewayModelId());
            ModelEntity model = modelRepository.findById(descriptor.gatewayModelId())
                    .orElseGet(() -> modelRepository.save(new ModelEntity(
                            descriptor.gatewayModelId(),
                            descriptor.displayName(),
                            descriptor.family(),
                            false,
                            descriptor.supportsVision(),
                            descriptor.supportsTools(),
                            descriptor.supportsReasoning(),
                            descriptor.supportsStreaming(),
                            descriptor.imageGeneration()
                    )));
            model.refreshMetadata(
                    descriptor.displayName(),
                    descriptor.family(),
                    false,
                    descriptor.supportsVision(),
                    descriptor.supportsTools(),
                    descriptor.supportsReasoning(),
                    descriptor.supportsStreaming(),
                    descriptor.imageGeneration(),
                    descriptor.contextWindowTokens(),
                    descriptor.inputCostMicrosPerToken(),
                    descriptor.outputCostMicrosPerToken()
            );
            model.setArchived(false);
            modelRepository.save(model);
            ModelProviderMappingEntity mapping = mappingRepository.findByModelIdOrderByPriorityAscProviderIdAsc(model.getId()).stream()
                    .filter(candidate -> candidate.getProvider().getId().equals(providerId))
                    .findFirst()
                    .orElse(null);
            if (mapping == null) {
                mapping = ModelProviderMappingEntity.of(
                        model,
                        provider,
                        descriptor.providerModelName(),
                        descriptor.supportsStreaming(),
                        descriptor.supportsVision(),
                        descriptor.supportsTools(),
                        descriptor.supportsReasoning(),
                        descriptor.imageGeneration(),
                        descriptor.priority()
                );
                synced++;
            } else {
                mapping.refresh(
                        descriptor.providerModelName(),
                        descriptor.supportsStreaming(),
                        descriptor.supportsVision(),
                        descriptor.supportsTools(),
                        descriptor.supportsReasoning(),
                        descriptor.imageGeneration(),
                        descriptor.priority(),
                        true,
                        true
                );
            }
            if (!mapping.isSyncManaged()) {
                mapping.refresh(
                        mapping.getModelName(),
                        mapping.isStreaming(),
                        mapping.isVision(),
                        mapping.isTools(),
                        mapping.isReasoning(),
                        mapping.isImageGeneration(),
                        mapping.getPriority(),
                        mapping.isActive(),
                        true
                );
            }
            mappingRepository.save(mapping);
        }
        int archivedMappings = archiveMissingMappings(providerId, discoveredModelIds);
        historyRepository.save(new ModelSyncHistoryEntity(
                providerId,
                "SUCCESS",
                descriptors.size(),
                synced,
                archivedMappings,
                "discovered=" + descriptors.stream().map(ProviderModelDescriptor::gatewayModelId).sorted().toList()
        ));
        return synced;
    }

    public List<ModelSyncHistoryEntity> history(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return historyRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return historyRepository.findByProviderId(providerId, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private int archiveMissingMappings(String providerId, Set<String> discoveredModelIds) {
        int archived = 0;
        for (ModelProviderMappingEntity mapping : mappingRepository.findAll()) {
            if (!mapping.getProvider().getId().equals(providerId)) {
                continue;
            }
            if (!mapping.isSyncManaged()) {
                continue;
            }
            boolean shouldBeActive = discoveredModelIds.contains(mapping.getModel().getId());
            if (mapping.isActive() != shouldBeActive) {
                mapping.refresh(
                        mapping.getModelName(),
                        mapping.isStreaming(),
                        mapping.isVision(),
                        mapping.isTools(),
                        mapping.isReasoning(),
                        mapping.isImageGeneration(),
                        mapping.getPriority(),
                        shouldBeActive,
                        true
                );
                mappingRepository.save(mapping);
                if (!shouldBeActive) {
                    archived++;
                }
            }
            refreshModelArchiveState(mapping.getModel().getId());
        }
        return archived;
    }

    private void refreshModelArchiveState(String modelId) {
        ModelEntity model = modelRepository.findById(modelId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown model: " + modelId));
        boolean hasActiveMappings = mappingRepository.findByModelIdOrderByPriorityAscProviderIdAsc(modelId).stream()
                .anyMatch(ModelProviderMappingEntity::isActive);
        model.setArchived(!hasActiveMappings);
        modelRepository.save(model);
    }
}
