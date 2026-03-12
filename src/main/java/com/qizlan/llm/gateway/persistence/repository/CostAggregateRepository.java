package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.CostAggregateEntity;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostAggregateRepository extends JpaRepository<CostAggregateEntity, String> {

    List<CostAggregateEntity> findByBucketType(String bucketType, Sort sort);

    CostAggregateEntity findTopByBucketTypeOrderByUpdatedAtDesc(String bucketType);

    void deleteByBucketType(String bucketType);
}
