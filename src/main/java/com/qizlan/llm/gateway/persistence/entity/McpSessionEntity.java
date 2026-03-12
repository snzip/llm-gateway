package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mcp_session")
public class McpSessionEntity {

    @Id
    private String sessionId;

    @Column(nullable = false)
    private String protocolVersion;

    @Column(nullable = false)
    private String clientName;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected McpSessionEntity() {
    }

    public McpSessionEntity(String protocolVersion, String clientName) {
        this.protocolVersion = protocolVersion;
        this.clientName = clientName;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        if (sessionId == null) {
            sessionId = "mcp-session-" + UUID.randomUUID().toString().replace("-", "");
        }
        createdAt = OffsetDateTime.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public String getClientName() {
        return clientName;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
