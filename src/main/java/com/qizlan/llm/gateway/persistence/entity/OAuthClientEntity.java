package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "oauth_client")
public class OAuthClientEntity {

    @Id
    private String clientId;

    @Column(nullable = false)
    private String clientSecret;

    @Column(nullable = false)
    private String clientName;

    @Column(nullable = false)
    private String grantTypes;

    private String redirectUri;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OAuthClientEntity() {
    }

    public OAuthClientEntity(String clientName, String grantTypes, String redirectUri) {
        this.clientName = clientName;
        this.grantTypes = grantTypes;
        this.redirectUri = redirectUri;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        if (clientId == null) {
            clientId = "mcp-client-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        if (clientSecret == null) {
            clientSecret = "mcp-secret-" + UUID.randomUUID().toString().replace("-", "");
        }
        createdAt = OffsetDateTime.now();
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getClientName() {
        return clientName;
    }

    public String getGrantTypes() {
        return grantTypes;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public boolean isActive() {
        return active;
    }
}
