package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "projection_state")
public class ProjectionStateEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Column(nullable = false)
    private String lastReason;

    protected ProjectionStateEntity() {
    }

    public ProjectionStateEntity(String id, String lastReason) {
        this.id = id;
        this.version = 1L;
        this.updatedAt = OffsetDateTime.now();
        this.lastReason = lastReason == null ? "" : lastReason;
    }

    public String getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getLastReason() {
        return lastReason;
    }

    public void bump(String reason) {
        version += 1L;
        updatedAt = OffsetDateTime.now();
        lastReason = reason == null ? "" : reason;
    }
}
