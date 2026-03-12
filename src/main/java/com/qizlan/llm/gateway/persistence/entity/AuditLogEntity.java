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
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String organizationId;

    @Column(nullable = false)
    private String actorType;

    @Column(nullable = false)
    private String actorId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private String resourceId;

    @Lob
    @Column(nullable = false)
    private String detailJson;

    @Column(nullable = false)
    private String correlationId;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AuditLogEntity() {
    }

    public AuditLogEntity(
            String organizationId,
            String actorType,
            String actorId,
            String action,
            String resourceType,
            String resourceId,
            String detailJson,
            String correlationId
    ) {
        this.organizationId = organizationId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.detailJson = detailJson;
        this.correlationId = correlationId;
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

    public String getOrganizationId() {
        return organizationId;
    }

    public String getActorType() {
        return actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
