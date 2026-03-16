package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.ProviderProbeHistoryEntity;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderProbeHistoryRepository extends JpaRepository<ProviderProbeHistoryEntity, String> {

    List<ProviderProbeHistoryEntity> findByProviderId(String providerId, Sort sort);
}
