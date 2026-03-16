package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.ProjectionStateEntity;
import com.qizlan.llm.gateway.persistence.repository.ProjectionStateRepository;

public class RoutingProjectionStateService {

    public static final String ROUTING_METADATA_PROJECTION = "routing_metadata";

    private final ProjectionStateRepository projectionStateRepository;

    public RoutingProjectionStateService(ProjectionStateRepository projectionStateRepository) {
        this.projectionStateRepository = projectionStateRepository;
    }

    public ProjectionVersion currentVersion() {
        return projectionStateRepository.findById(ROUTING_METADATA_PROJECTION)
                .map(entity -> new ProjectionVersion(entity.getVersion(), entity.getUpdatedAt().toInstant().toEpochMilli()))
                .orElseGet(() -> new ProjectionVersion(0L, 0L));
    }

    public ProjectionVersion markRoutingMetadataChanged(String reason) {
        ProjectionStateEntity entity = projectionStateRepository.findById(ROUTING_METADATA_PROJECTION)
                .orElseGet(() -> new ProjectionStateEntity(ROUTING_METADATA_PROJECTION, reason));
        if (entity.getVersion() > 0L) {
            entity.bump(reason);
        }
        ProjectionStateEntity saved = projectionStateRepository.save(entity);
        return new ProjectionVersion(saved.getVersion(), saved.getUpdatedAt().toInstant().toEpochMilli());
    }

    public record ProjectionVersion(long version, long updatedAtMillis) {
    }
}
