package com.qizlan.llm.gateway.gateway.service;

public interface ProviderAvailabilityPolicy {

    boolean isAvailable(String providerId);
}
