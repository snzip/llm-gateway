package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.McpSessionEntity;
import com.qizlan.llm.gateway.persistence.repository.McpSessionRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class McpSessionService {

    private static final String SERVER_PROTOCOL_VERSION = "2026-03-01";

    private final McpSessionRepository mcpSessionRepository;

    public McpSessionService(McpSessionRepository mcpSessionRepository) {
        this.mcpSessionRepository = mcpSessionRepository;
    }

    public Map<String, Object> initialize(Map<String, Object> request) {
        String requestedVersion = request == null ? SERVER_PROTOCOL_VERSION : stringValue(request, "protocol_version", SERVER_PROTOCOL_VERSION);
        String clientName = request == null ? "unknown-client" : stringValue(request, "client_name", "unknown-client");
        McpSessionEntity session = mcpSessionRepository.save(new McpSessionEntity(
                requestedVersion.compareTo(SERVER_PROTOCOL_VERSION) > 0 ? SERVER_PROTOCOL_VERSION : requestedVersion,
                clientName
        ));
        return Map.of(
                "session_id", session.getSessionId(),
                "protocol_version", session.getProtocolVersion(),
                "server_name", "llmgatejava-mcp",
                "capabilities", Map.of(
                        "tools", true,
                        "ping", true
                )
        );
    }

    public Map<String, Object> ping(String sessionId) {
        McpSessionEntity session = mcpSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP session: " + sessionId));
        if (!session.isActive()) {
            throw new IllegalArgumentException("Inactive MCP session: " + sessionId);
        }
        return Map.of(
                "session_id", session.getSessionId(),
                "protocol_version", session.getProtocolVersion(),
                "pong", true
        );
    }

    private String stringValue(Map<String, Object> request, String key, String fallback) {
        Object value = request.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
