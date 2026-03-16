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
    private String requestId;

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

    @Column(nullable = false)
    private long promptCostMicrosUsd;

    @Column(nullable = false)
    private long completionCostMicrosUsd;

    @Column(nullable = false)
    private int reasoningTokens;

    @Column(nullable = false)
    private int cachedTokens;

    @Column(nullable = false)
    private int imageCount;

    @Column(nullable = false)
    private long timeToFirstTokenMs;

    @Column(nullable = false)
    private boolean streamed;

    @Column(nullable = false)
    private boolean canceled;

    @Column(nullable = false)
    private boolean retried;

    @Column(nullable = false)
    private boolean hasError;

    private String apiKeyId;

    private String organizationId;

    private String projectId;

    @Lob
    private String routingTrace;

    private String correlationId;

    private String traceId;

    private String spanId;

    @Lob
    private String requestPayload;

    @Lob
    private String responsePayload;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RequestLogEntity() {
    }

    public RequestLogEntity(
            String requestId,
            String path,
            String requestedModel,
            String providerId,
            int httpStatus,
            long latencyMs,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long estimatedCostMicrosUsd,
            long promptCostMicrosUsd,
            long completionCostMicrosUsd,
            int reasoningTokens,
            int cachedTokens,
            int imageCount,
            long timeToFirstTokenMs,
            boolean streamed,
            boolean canceled,
            boolean retried,
            boolean hasError,
            String apiKeyId,
            String organizationId,
            String projectId,
            String routingTrace,
            String correlationId,
            String traceId,
            String spanId,
            String requestPayload,
            String responsePayload
    ) {
        this.requestId = requestId;
        this.path = path;
        this.requestedModel = requestedModel;
        this.providerId = providerId;
        this.httpStatus = httpStatus;
        this.latencyMs = latencyMs;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.estimatedCostMicrosUsd = estimatedCostMicrosUsd;
        this.promptCostMicrosUsd = promptCostMicrosUsd;
        this.completionCostMicrosUsd = completionCostMicrosUsd;
        this.reasoningTokens = reasoningTokens;
        this.cachedTokens = cachedTokens;
        this.imageCount = imageCount;
        this.timeToFirstTokenMs = timeToFirstTokenMs;
        this.streamed = streamed;
        this.canceled = canceled;
        this.retried = retried;
        this.hasError = hasError;
        this.apiKeyId = apiKeyId;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.routingTrace = routingTrace;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.spanId = spanId;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
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

    public String getRequestId() {
        return requestId;
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

    public long getPromptCostMicrosUsd() {
        return promptCostMicrosUsd;
    }

    public long getCompletionCostMicrosUsd() {
        return completionCostMicrosUsd;
    }

    public int getReasoningTokens() {
        return reasoningTokens;
    }

    public int getCachedTokens() {
        return cachedTokens;
    }

    public int getImageCount() {
        return imageCount;
    }

    public long getTimeToFirstTokenMs() {
        return timeToFirstTokenMs;
    }

    public boolean isStreamed() {
        return streamed;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public boolean isRetried() {
        return retried;
    }

    public boolean isHasError() {
        return hasError;
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

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public String getResponsePayload() {
        return responsePayload;
    }
}
