package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.gateway.dto.ModelDto;
import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import com.qizlan.llm.gateway.persistence.repository.ModelProviderMappingRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModelCatalogService {

    private final ModelProviderMappingRepository mappingRepository;
    private final RoutingProjectionInvalidationService routingProjectionInvalidationService;

    public ModelCatalogService(
            ModelProviderMappingRepository mappingRepository,
            RoutingProjectionInvalidationService routingProjectionInvalidationService
    ) {
        this.mappingRepository = mappingRepository;
        this.routingProjectionInvalidationService = routingProjectionInvalidationService;
    }

    public List<ModelDto> listModels() {
        routingProjectionInvalidationService.refreshIfNeeded();
        Map<String, List<ModelProviderMappingEntity>> grouped = mappingRepository.findAll().stream()
                .filter(ModelProviderMappingEntity::isActive)
                .filter(mapping -> !mapping.getModel().isArchived())
                .collect(Collectors.groupingBy(mapping -> mapping.getModel().getId()));

        return grouped.values().stream()
                .map(group -> {
                    ModelProviderMappingEntity first = group.get(0);
                    List<String> inputModalities = first.getModel().isSupportsVision() ? List.of("text", "image") : List.of("text");
                    List<String> outputModalities = first.getModel().isImageGeneration() ? List.of("text", "image") : List.of("text");
                    return new ModelDto(
                            first.getModel().getId(),
                            first.getModel().getName(),
                            first.getModel().getFamily(),
                            first.getModel().isFreeModel(),
                            first.getModel().getContextWindowTokens(),
                            first.getModel().getInputCostMicrosPerToken(),
                            first.getModel().getOutputCostMicrosPerToken(),
                            new ModelDto.Architecture(inputModalities, outputModalities),
                            group.stream()
                                    .map(mapping -> new ModelDto.ProviderSupport(
                                            mapping.getProvider().getId(),
                                            mapping.getModelName(),
                                            mapping.isStreaming(),
                                            mapping.isVision(),
                                            mapping.isTools(),
                                            mapping.isReasoning()))
                                    .toList()
                    );
                })
                .toList();
    }
}
