package com.qizlan.llm.gateway.gateway.provider;

public class UpstreamProviderException extends RuntimeException {

    private final String providerId;
    private final int statusCode;
    private final int gatewayStatus;
    private final String errorType;
    private final boolean retryable;

    public UpstreamProviderException(String providerId, int statusCode, int gatewayStatus, String errorType, boolean retryable, String message) {
        super(message);
        this.providerId = providerId;
        this.statusCode = statusCode;
        this.gatewayStatus = gatewayStatus;
        this.errorType = errorType;
        this.retryable = retryable;
    }

    public static UpstreamProviderException fromStatus(String providerId, int statusCode, String message) {
        if (statusCode == 429) {
            return new UpstreamProviderException(providerId, statusCode, 429, "rate_limited", true, message);
        }
        if (statusCode >= 400 && statusCode < 500) {
            return new UpstreamProviderException(providerId, statusCode, statusCode, "upstream_client_error", false, message);
        }
        return new UpstreamProviderException(providerId, statusCode, 502, "upstream_server_error", true, message);
    }

    public static UpstreamProviderException timeout(String providerId, String message) {
        return new UpstreamProviderException(providerId, 504, 504, "timeout", true, message);
    }

    public static UpstreamProviderException network(String providerId, String message) {
        return new UpstreamProviderException(providerId, 503, 503, "network_error", true, message);
    }

    public String getProviderId() {
        return providerId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getGatewayStatus() {
        return gatewayStatus;
    }

    public String getErrorType() {
        return errorType;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
