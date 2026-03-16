package com.qizlan.llm.gateway.gateway.provider;

public record ProviderStreamEvent(
        String eventName,
        String data,
        boolean done
) {
}
