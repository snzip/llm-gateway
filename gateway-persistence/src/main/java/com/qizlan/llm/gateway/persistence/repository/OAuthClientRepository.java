package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.OAuthClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthClientRepository extends JpaRepository<OAuthClientEntity, String> {
}
