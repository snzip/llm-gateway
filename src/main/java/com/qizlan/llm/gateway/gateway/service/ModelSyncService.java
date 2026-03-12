package com.qizlan.llm.gateway.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
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
    private final ObjectMapper objectMapper;

    public ModelSyncService(
            List<ProviderAdapter> providerAdapters,
            ProviderRepository providerRepository,
            ModelRepository modelRepository,
            ModelProviderMappingRepository mappingRepository,
            ModelSyncHistoryRepository historyRepository,
            GatewayProperties properties,
            ObjectMapper objectMapper
    ) {
        this.providerAdapters = providerAdapters;
        this.providerRepository = providerRepository;
        this.modelRepository = modelRepository;
        this.mappingRepository = mappingRepository;
        this.historyRepository = historyRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
        ConflictPolicy conflictPolicy = resolveConflictPolicy();
        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider entity: " + providerId));
        List<ProviderModelDescriptor> descriptors = adapter.listModels();
        Set<String> discoveredModelIds = new HashSet<>();
        List<Map<String, Object>> changes = new java.util.ArrayList<>();
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
            Map<String, Object> beforeModel = modelSnapshot(model);
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
            recordDiff(changes, "model", model.getId(), beforeModel, modelSnapshot(model));
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
                mapping.refresh(
                        mapping.getModelName(),
                        mapping.isStreaming(),
                        mapping.isVision(),
                        mapping.isTools(),
                        mapping.isReasoning(),
                        mapping.isImageGeneration(),
                        mapping.getPriority(),
                        true,
                        true
                );
                synced++;
                changes.add(Map.of(
                        "resource_type", "mapping",
                        "resource_id", providerId + ":" + model.getId(),
                        "change_type", "created",
                        "after", mappingSnapshot(mapping)
                ));
            } else {
                Map<String, Object> beforeMapping = mappingSnapshot(mapping);
                if (!mapping.isSyncManaged() && conflictPolicy == ConflictPolicy.PRESERVE_MANUAL) {
                    changes.add(Map.of(
                            "resource_type", "mapping",
                            "resource_id", providerId + ":" + model.getId(),
                            "change_type", "conflict_skipped",
                            "policy", conflictPolicy.name(),
                            "before", beforeMapping
                    ));
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
                    recordDiff(changes, "mapping", providerId + ":" + model.getId(), beforeMapping, mappingSnapshot(mapping));
                }
            }
            mappingRepository.save(mapping);
        }
        int archivedMappings = archiveMissingMappings(providerId, discoveredModelIds, changes, conflictPolicy);
        historyRepository.save(new ModelSyncHistoryEntity(
                providerId,
                "SUCCESS",
                descriptors.size(),
                synced,
                archivedMappings,
                serializeDetail(descriptors.stream().map(ProviderModelDescriptor::gatewayModelId).sorted().toList(), changes, conflictPolicy)
        ));
        return synced;
    }

    public List<ModelSyncHistoryEntity> history(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return historyRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return historyRepository.findByProviderId(providerId, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private int archiveMissingMappings(String providerId, Set<String> discoveredModelIds, List<Map<String, Object>> changes, ConflictPolicy conflictPolicy) {
        int archived = 0;
        for (ModelProviderMappingEntity mapping : mappingRepository.findAll()) {
            if (!mapping.getProvider().getId().equals(providerId)) {
                continue;
            }
            if (!mapping.isSyncManaged()) {
                if (conflictPolicy == ConflictPolicy.PRESERVE_MANUAL) {
                    changes.add(Map.of(
                            "resource_type", "mapping",
                            "resource_id", providerId + ":" + mapping.getModel().getId(),
                            "change_type", "archive_skipped",
                            "policy", conflictPolicy.name(),
                            "active", mapping.isActive()
                    ));
                }
                continue;
            }
            boolean shouldBeActive = discoveredModelIds.contains(mapping.getModel().getId());
            if (mapping.isActive() != shouldBeActive) {
                Map<String, Object> beforeMapping = mappingSnapshot(mapping);
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
                recordDiff(changes, "mapping", providerId + ":" + mapping.getModel().getId(), beforeMapping, mappingSnapshot(mapping));
                if (!shouldBeActive) {
                    archived++;
                }
            }
            refreshModelArchiveState(mapping.getModel().getId());
        }
        return archived;
    }

    private void recordDiff(
            List<Map<String, Object>> changes,
            String resourceType,
            String resourceId,
            Map<String, Object> before,
            Map<String, Object> after
    ) {
        Map<String, Map<String, Object>> fieldChanges = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            Object beforeValue = before.get(entry.getKey());
            Object afterValue = entry.getValue();
            if (!java.util.Objects.equals(beforeValue, afterValue)) {
                fieldChanges.put(entry.getKey(), Map.of(
                        "before", beforeValue == null ? "" : beforeValue,
                        "after", afterValue == null ? "" : afterValue
                ));
            }
        }
        if (!fieldChanges.isEmpty()) {
            changes.add(Map.of(
                    "resource_type", resourceType,
                    "resource_id", resourceId,
                    "change_type", "updated",
                    "fields", fieldChanges
            ));
        }
    }

    private Map<String, Object> modelSnapshot(ModelEntity model) {
        return Map.ofEntries(
                Map.entry("name", model.getName()),
                Map.entry("family", model.getFamily()),
                Map.entry("supportsVision", model.isSupportsVision()),
                Map.entry("supportsTools", model.isSupportsTools()),
                Map.entry("supportsReasoning", model.isSupportsReasoning()),
                Map.entry("supportsStreaming", model.isSupportsStreaming()),
                Map.entry("imageGeneration", model.isImageGeneration()),
                Map.entry("contextWindowTokens", numberOrZero(model.getContextWindowTokens())),
                Map.entry("inputCostMicrosPerToken", numberOrZero(model.getInputCostMicrosPerToken())),
                Map.entry("outputCostMicrosPerToken", numberOrZero(model.getOutputCostMicrosPerToken())),
                Map.entry("archived", model.isArchived())
        );
    }

    private Map<String, Object> mappingSnapshot(ModelProviderMappingEntity mapping) {
        return Map.ofEntries(
                Map.entry("modelName", mapping.getModelName()),
                Map.entry("streaming", mapping.isStreaming()),
                Map.entry("vision", mapping.isVision()),
                Map.entry("tools", mapping.isTools()),
                Map.entry("reasoning", mapping.isReasoning()),
                Map.entry("imageGeneration", mapping.isImageGeneration()),
                Map.entry("priority", mapping.getPriority()),
                Map.entry("active", mapping.isActive()),
                Map.entry("syncManaged", mapping.isSyncManaged())
        );
    }

    private String serializeDetail(List<String> discoveredModels, List<Map<String, Object>> changes, ConflictPolicy conflictPolicy) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "conflict_policy", conflictPolicy.name(),
                    "discovered_models", discoveredModels,
                    "changes", changes
            ));
        } catch (JsonProcessingException ex) {
            return "{\"conflict_policy\":\"UNKNOWN\",\"discovered_models\":[],\"changes\":[]}";
        }
    }

    private ConflictPolicy resolveConflictPolicy() {
        if (properties.sync() == null || properties.sync().conflictPolicy() == null || properties.sync().conflictPolicy().isBlank()) {
            return ConflictPolicy.OVERRIDE_MANUAL;
        }
        return ConflictPolicy.valueOf(properties.sync().conflictPolicy().trim().toUpperCase());
    }

    private Number numberOrZero(Number value) {
        return value == null ? 0 : value;
    }

    private enum ConflictPolicy {
        OVERRIDE_MANUAL,
        PRESERVE_MANUAL
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
