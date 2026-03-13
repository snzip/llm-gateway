package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.gateway.service.RequestLogPayloadSanitizer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLogPayloadSanitizerTest extends BaseGatewayTest {

    @Autowired
    private RequestLogPayloadSanitizer sanitizer;

    @Test
    void sanitizerRedactsSensitiveFields() {
        String sanitized = sanitizer.sanitize(Map.of(
                "client_secret", "top-secret",
                "access_token", "token-value",
                "nested", Map.of("authorization", "Bearer abc123")
        ));

        assertTrue(sanitized.contains("[REDACTED]"));
        assertFalse(sanitized.contains("top-secret"));
        assertFalse(sanitized.contains("token-value"));
        assertFalse(sanitized.contains("abc123"));
    }
}
