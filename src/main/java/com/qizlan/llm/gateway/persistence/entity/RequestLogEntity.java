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
@Table(name = "request_log")
public class RequestLogEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private String requestedModel;

    @Column(nullable = false)
    private String providerId;

    @Column(nullable = false)
    private int httpStatus;

    @Column(nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private int promptTokens;

    @Column(nullable = false)
    private int completionTokens;

    @Column(nullable = false)
    private int totalTokens;

    @Column(nullable = false)
    private long estimatedCostMicrosUsd;

    private String apiKeyId;

    private String organizationId;

    private String projectId;

    @Lob
    private String routingTrace;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RequestLogEntity() {
    }

    public RequestLogEntity(
            String path,
            String requestedModel,
            String providerId,
            int httpStatus,
            long latencyMs,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long estimatedCostMicrosUsd,
            String apiKeyId,
            String organizationId,
            String projectId,
            String routingTrace
    ) {
        this.path = path;
        this.requestedModel = requestedModel;
        this.providerId = providerId;
        this.httpStatus = httpStatus;
        this.latencyMs = latencyMs;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.estimatedCostMicrosUsd = estimatedCostMicrosUsd;
        this.apiKeyId = apiKeyId;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.routingTrace = routingTrace;
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

    public String getPath() {
        return path;
    }

    public String getRequestedModel() {
        return requestedModel;
    }

    public String getProviderId() {
        return providerId;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public long getLatencyMs() {
        return latencyMs;
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

    public long getEstimatedCostMicrosUsd() {
        return estimatedCostMicrosUsd;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getProjectId() {
        return projectId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getRoutingTrace() {
        return routingTrace;
    }
}
