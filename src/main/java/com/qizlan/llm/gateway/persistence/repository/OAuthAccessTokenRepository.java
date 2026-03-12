package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.OAuthAccessTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccessTokenRepository extends JpaRepository<OAuthAccessTokenEntity, String> {
}
