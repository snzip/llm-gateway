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
@Table(name = "guardrail_violation")
public class GuardrailViolationEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String organizationId;

    @Column(nullable = false)
    private String ruleId;

    @Column(nullable = false)
    private String ruleName;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private String action;

    @Lob
    @Column(nullable = false)
    private String matchedText;

    private String apiKeyId;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected GuardrailViolationEntity() {
    }

    public GuardrailViolationEntity(String organizationId, String ruleId, String ruleName, String path, String action, String matchedText, String apiKeyId) {
        this.organizationId = organizationId;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.path = path;
        this.action = action;
        this.matchedText = matchedText;
        this.apiKeyId = apiKeyId;
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

    public String getRuleId() {
        return ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getPath() {
        return path;
    }

    public String getAction() {
        return action;
    }

    public String getMatchedText() {
        return matchedText;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
