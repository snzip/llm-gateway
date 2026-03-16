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

    private String refreshToken;

    @Column(nullable = false)
    private boolean revoked;

    private OffsetDateTime revokedAt;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OAuthAccessTokenEntity() {
    }

    public OAuthAccessTokenEntity(String clientId, String scope, long expiresInSeconds) {
        this.clientId = clientId;
        this.scope = scope;
        this.expiresInSeconds = expiresInSeconds;
        this.revoked = false;
    }

    @PrePersist
    void onCreate() {
        if (accessToken == null) {
            accessToken = "mcp_" + UUID.randomUUID().toString().replace("-", "");
        }
        if (refreshToken == null) {
            refreshToken = "mcp_refresh_" + UUID.randomUUID().toString().replace("-", "");
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

    public String getRefreshToken() {
        return refreshToken;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isExpired() {
        return createdAt != null && createdAt.plusSeconds(expiresInSeconds).isBefore(OffsetDateTime.now());
    }

    public void revoke() {
        this.revoked = true;
        this.revokedAt = OffsetDateTime.now();
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
