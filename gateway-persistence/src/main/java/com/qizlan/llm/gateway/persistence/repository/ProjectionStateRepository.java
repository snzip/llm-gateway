package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.ProjectionStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectionStateRepository extends JpaRepository<ProjectionStateEntity, String> {
}
