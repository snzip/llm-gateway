package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_probe_history")
public class ProviderProbeHistoryEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String providerId;

    @Column(nullable = false)
    private boolean healthy;

    @Column(nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private String probeModel;

    @Column(nullable = false)
    private String strategy;

    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ProviderProbeHistoryEntity() {
    }

    public ProviderProbeHistoryEntity(String providerId, boolean healthy, long latencyMs, String probeModel, String strategy, String errorMessage) {
        this.providerId = providerId;
        this.healthy = healthy;
        this.latencyMs = latencyMs;
        this.probeModel = probeModel;
        this.strategy = strategy;
        this.errorMessage = errorMessage;
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

    public boolean isHealthy() {
        return healthy;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getProbeModel() {
        return probeModel;
    }

    public String getStrategy() {
        return strategy;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
