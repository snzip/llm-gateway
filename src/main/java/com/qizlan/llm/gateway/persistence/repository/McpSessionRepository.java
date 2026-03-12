package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.McpSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpSessionRepository extends JpaRepository<McpSessionEntity, String> {
}
