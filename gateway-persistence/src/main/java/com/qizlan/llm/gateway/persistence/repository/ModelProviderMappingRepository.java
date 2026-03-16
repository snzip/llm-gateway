package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelProviderMappingRepository extends JpaRepository<ModelProviderMappingEntity, Long> {

    @EntityGraph(attributePaths = {"model", "provider"})
    List<ModelProviderMappingEntity> findAll();

    @EntityGraph(attributePaths = {"model", "provider"})
    Optional<ModelProviderMappingEntity> findFirstByModelIdOrderByProviderIdAsc(String modelId);

    @EntityGraph(attributePaths = {"model", "provider"})
    List<ModelProviderMappingEntity> findByModelIdOrderByPriorityAscProviderIdAsc(String modelId);

    boolean existsByModelIdAndProviderId(String modelId, String providerId);
}
