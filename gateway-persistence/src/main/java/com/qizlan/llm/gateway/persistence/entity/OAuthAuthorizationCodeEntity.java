package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "oauth_authorization_code")
public class OAuthAuthorizationCodeEntity {

    @Id
    private String code;

    @Column(nullable = false)
    private String clientId;

    private String redirectUri;

    private String state;

    @Column(nullable = false)
    private boolean consumed;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OAuthAuthorizationCodeEntity() {
    }

    public OAuthAuthorizationCodeEntity(String clientId, String redirectUri, String state) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.state = state;
        this.consumed = false;
    }

    @PrePersist
    void onCreate() {
        if (code == null) {
            code = "mcp-code-" + UUID.randomUUID().toString().replace("-", "");
        }
        createdAt = OffsetDateTime.now();
    }

    public String getCode() {
        return code;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getState() {
        return state;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }
}
