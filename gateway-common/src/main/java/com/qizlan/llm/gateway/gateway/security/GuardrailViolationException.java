package com.qizlan.llm.gateway.gateway.security;

public class GuardrailViolationException extends RuntimeException {

    public GuardrailViolationException(String message) {
        super(message);
    }
}
