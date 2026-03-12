package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {

    @EntityGraph(attributePaths = {"organization", "project", "project.organization"})
    Optional<ApiKeyEntity> findByTokenHashAndActiveTrue(String tokenHash);

    @Override
    @EntityGraph(attributePaths = {"organization", "project", "project.organization"})
    List<ApiKeyEntity> findAll();

    @Override
    @EntityGraph(attributePaths = {"organization", "project", "project.organization"})
    Optional<ApiKeyEntity> findById(String id);
}
