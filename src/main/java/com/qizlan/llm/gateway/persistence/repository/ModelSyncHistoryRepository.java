package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.ModelSyncHistoryEntity;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelSyncHistoryRepository extends JpaRepository<ModelSyncHistoryEntity, String> {

    List<ModelSyncHistoryEntity> findByProviderId(String providerId, Sort sort);
}
