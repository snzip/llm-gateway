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
@Table(name = "guardrail_rule")
public class GuardrailRuleEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String organizationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String ruleType;

    @Column(nullable = false)
    private String pattern;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected GuardrailRuleEntity() {
    }

    public GuardrailRuleEntity(String organizationId, String name, String ruleType, String pattern, String action, boolean active) {
        this.organizationId = organizationId;
        this.name = name;
        this.ruleType = ruleType;
        this.pattern = pattern;
        this.action = action;
        this.active = active;
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

    public String getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getRuleType() {
        return ruleType;
    }

    public String getPattern() {
        return pattern;
    }

    public String getAction() {
        return action;
    }

    public boolean isActive() {
        return active;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
