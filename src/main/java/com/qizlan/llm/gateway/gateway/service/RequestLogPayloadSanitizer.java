package com.qizlan.llm.gateway.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qizlan.llm.gateway.config.GatewayProperties;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RequestLogPayloadSanitizer {

    private static final String REDACTED = "[REDACTED]";

    private final ObjectMapper objectMapper;
    private final GatewayProperties properties;

    public RequestLogPayloadSanitizer(ObjectMapper objectMapper, GatewayProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String sanitize(Object payload) {
        if (payload == null) {
            return "";
        }
        try {
            JsonNode root = objectMapper.valueToTree(payload);
            redact(root, redactedFields());
            return truncate(objectMapper.writeValueAsString(root));
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            return truncate(redactString(payload.toString()));
        }
    }

    private void redact(JsonNode node, Set<String> redactedFields) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.fieldNames().forEachRemaining(field -> {
                JsonNode child = objectNode.get(field);
                if (redactedFields.contains(field.toLowerCase(Locale.ROOT))) {
                    objectNode.put(field, REDACTED);
                } else {
                    redact(child, redactedFields);
                }
            });
            return;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode child : arrayNode) {
                redact(child, redactedFields);
            }
        }
    }

    private String redactString(String value) {
        String sanitized = value;
        for (String field : redactedFields()) {
            sanitized = sanitized.replaceAll("(?i)(" + java.util.regex.Pattern.quote(field) + "\\s*[=:]\\s*)([^,\\s}]+)", "$1" + REDACTED);
        }
        sanitized = sanitized.replaceAll("(?i)bearer\\s+[a-z0-9._\\-]+", "Bearer " + REDACTED);
        return sanitized;
    }

    private String truncate(String value) {
        int max = Math.max(0, properties.requestLog().maxBodyChars());
        if (max == 0 || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...(truncated)";
    }

    private Set<String> redactedFields() {
        return new HashSet<>(properties.requestLog().redactedFields());
    }
}
