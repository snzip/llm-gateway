package com.qizlan.llm.gateway.gateway.provider;

public class UpstreamProviderException extends RuntimeException {

    private final String providerId;
    private final int statusCode;

    public UpstreamProviderException(String providerId, int statusCode, String message) {
        super(message);
        this.providerId = providerId;
        this.statusCode = statusCode;
    }

    public String getProviderId() {
        return providerId;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
