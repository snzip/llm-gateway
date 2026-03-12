package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "model_sync_history")
public class ModelSyncHistoryEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String providerId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int discoveredModels;

    @Column(nullable = false)
    private int syncedMappings;

    @Column(nullable = false)
    private int archivedMappings;

    @Lob
    private String detail;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ModelSyncHistoryEntity() {
    }

    public ModelSyncHistoryEntity(String providerId, String status, int discoveredModels, int syncedMappings, int archivedMappings, String detail) {
        this.providerId = providerId;
        this.status = status;
        this.discoveredModels = discoveredModels;
        this.syncedMappings = syncedMappings;
        this.archivedMappings = archivedMappings;
        this.detail = detail;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "");
        }
        createdAt = OffsetDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getStatus() {
        return status;
    }

    public int getDiscoveredModels() {
        return discoveredModels;
    }

    public int getSyncedMappings() {
        return syncedMappings;
    }

    public int getArchivedMappings() {
        return archivedMappings;
    }

    public String getDetail() {
        return detail;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
