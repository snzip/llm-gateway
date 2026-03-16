package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_key")
public class ApiKeyEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private String tokenPrefix;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @Column(nullable = false)
    private long spentMicrosUsd;

    @Column(nullable = false)
    private long budgetMicrosUsd;

    @Column(nullable = false)
    private int requestsPerMinuteLimit;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected ApiKeyEntity() {
    }

    public ApiKeyEntity(String tokenHash, String tokenPrefix, String name, boolean active, OrganizationEntity organization, ProjectEntity project) {
        this.tokenHash = tokenHash;
        this.tokenPrefix = tokenPrefix;
        this.name = name;
        this.active = active;
        this.organization = organization;
        this.project = project;
        this.spentMicrosUsd = 0L;
        this.budgetMicrosUsd = 0L;
        this.requestsPerMinuteLimit = 0;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "");
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public ProjectEntity getProject() {
        return project;
    }

    public long getSpentMicrosUsd() {
        return spentMicrosUsd;
    }

    public long getBudgetMicrosUsd() {
        return budgetMicrosUsd;
    }

    public int getRequestsPerMinuteLimit() {
        return requestsPerMinuteLimit;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setBudgetMicrosUsd(long budgetMicrosUsd) {
        this.budgetMicrosUsd = budgetMicrosUsd;
    }

    public void setRequestsPerMinuteLimit(int requestsPerMinuteLimit) {
        this.requestsPerMinuteLimit = Math.max(requestsPerMinuteLimit, 0);
    }

    public void addSpentMicrosUsd(long delta) {
        this.spentMicrosUsd += delta;
    }
}
