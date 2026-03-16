package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.SessionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    @EntityGraph(attributePaths = {"user"})
    List<SessionEntity> findByTokenPrefix(String tokenPrefix);
}
