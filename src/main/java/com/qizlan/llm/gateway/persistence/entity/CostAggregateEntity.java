package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cost_aggregate")
public class CostAggregateEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String bucketType;

    @Column(nullable = false)
    private OffsetDateTime bucketStart;

    @Column(nullable = false)
    private String providerId;

    @Column(nullable = false)
    private String requestedModel;

    private String organizationId;

    private String projectId;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private long requestCount;

    @Column(nullable = false)
    private long estimatedCostMicrosUsd;

    @Column(nullable = false)
    private int promptTokens;

    @Column(nullable = false)
    private int completionTokens;

    @Column(nullable = false)
    private int totalTokens;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected CostAggregateEntity() {
    }

    public CostAggregateEntity(
            String bucketType,
            OffsetDateTime bucketStart,
            String providerId,
            String requestedModel,
            String organizationId,
            String projectId,
            String path,
            long requestCount,
            long estimatedCostMicrosUsd,
            int promptTokens,
            int completionTokens,
            int totalTokens
    ) {
        this.bucketType = bucketType;
        this.bucketStart = bucketStart;
        this.providerId = providerId;
        this.requestedModel = requestedModel;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.path = path;
        this.requestCount = requestCount;
        this.estimatedCostMicrosUsd = estimatedCostMicrosUsd;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "");
        }
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getBucketType() {
        return bucketType;
    }

    public OffsetDateTime getBucketStart() {
        return bucketStart;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getRequestedModel() {
        return requestedModel;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getPath() {
        return path;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public long getEstimatedCostMicrosUsd() {
        return estimatedCostMicrosUsd;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
