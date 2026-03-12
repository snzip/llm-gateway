package com.qizlan.llm.gateway.gateway.security;

public class ApiKeyAccessDeniedException extends RuntimeException {

    public ApiKeyAccessDeniedException(String message) {
        super(message);
    }
}
