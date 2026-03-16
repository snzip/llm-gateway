package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import java.util.List;

public interface ProviderAccessPolicy {

    List<ModelProviderMappingEntity> filterAllowedProviders(ApiKeyEntity apiKey, List<ModelProviderMappingEntity> candidates);
}
