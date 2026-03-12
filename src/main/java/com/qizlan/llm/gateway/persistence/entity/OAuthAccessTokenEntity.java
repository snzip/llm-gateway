package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "oauth_access_token")
public class OAuthAccessTokenEntity {

    @Id
    private String accessToken;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String scope;

    @Column(nullable = false)
    private long expiresInSeconds;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OAuthAccessTokenEntity() {
    }

    public OAuthAccessTokenEntity(String clientId, String scope, long expiresInSeconds) {
        this.clientId = clientId;
        this.scope = scope;
        this.expiresInSeconds = expiresInSeconds;
    }

    @PrePersist
    void onCreate() {
        if (accessToken == null) {
            accessToken = "mcp_" + UUID.randomUUID().toString().replace("-", "");
        }
        createdAt = OffsetDateTime.now();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getClientId() {
        return clientId;
    }

    public String getScope() {
        return scope;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
