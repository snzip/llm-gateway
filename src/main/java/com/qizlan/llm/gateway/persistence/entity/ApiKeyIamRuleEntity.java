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
@Table(name = "api_key_iam_rule")
public class ApiKeyIamRuleEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_key_id")
    private ApiKeyEntity apiKey;

    @Column(nullable = false)
    private String ruleType;

    @Column(nullable = false)
    private String effect;

    @Column(nullable = false)
    private String pattern;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected ApiKeyIamRuleEntity() {
    }

    public ApiKeyIamRuleEntity(ApiKeyEntity apiKey, String ruleType, String effect, String pattern, boolean active) {
        this.apiKey = apiKey;
        this.ruleType = ruleType;
        this.effect = effect;
        this.pattern = pattern;
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

    public ApiKeyEntity getApiKey() {
        return apiKey;
    }

    public String getRuleType() {
        return ruleType;
    }

    public String getEffect() {
        return effect;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean isActive() {
        return active;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
