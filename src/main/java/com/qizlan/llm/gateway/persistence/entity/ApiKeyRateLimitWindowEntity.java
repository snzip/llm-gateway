package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "api_key_rate_limit_window",
        uniqueConstraints = @UniqueConstraint(name = "uk_api_key_rate_limit_window", columnNames = {"apiKeyId", "windowMinute"})
)
public class ApiKeyRateLimitWindowEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String apiKeyId;

    @Column(nullable = false)
    private long windowMinute;

    @Column(nullable = false)
    private int requestCount;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected ApiKeyRateLimitWindowEntity() {
    }

    public ApiKeyRateLimitWindowEntity(String apiKeyId, long windowMinute, int requestCount) {
        this.apiKeyId = apiKeyId;
        this.windowMinute = windowMinute;
        this.requestCount = requestCount;
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

    public String getApiKeyId() {
        return apiKeyId;
    }

    public long getWindowMinute() {
        return windowMinute;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void increment() {
        this.requestCount++;
    }
}
