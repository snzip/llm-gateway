package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.OAuthAuthorizationCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAuthorizationCodeRepository extends JpaRepository<OAuthAuthorizationCodeEntity, String> {
}
