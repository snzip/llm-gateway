package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.gateway.provider.ProviderAdapter;

public interface ProviderSyncAdapterResolver {

    ProviderAdapter resolve(String providerId);
}
