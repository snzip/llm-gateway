package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.ApiKeyIamRuleEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyIamRuleRepository extends JpaRepository<ApiKeyIamRuleEntity, String> {

    @EntityGraph(attributePaths = {"apiKey", "apiKey.organization", "apiKey.project"})
    List<ApiKeyIamRuleEntity> findByApiKeyIdOrderByRuleTypeAscEffectAscPatternAsc(String apiKeyId);

    @Override
    @EntityGraph(attributePaths = {"apiKey", "apiKey.organization", "apiKey.project"})
    Optional<ApiKeyIamRuleEntity> findById(String id);
}
