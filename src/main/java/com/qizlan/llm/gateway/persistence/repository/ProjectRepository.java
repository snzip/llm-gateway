package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.ProjectEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {

    @Override
    @EntityGraph(attributePaths = "organization")
    List<ProjectEntity> findAll();

    @Override
    @EntityGraph(attributePaths = "organization")
    Optional<ProjectEntity> findById(String id);
}
