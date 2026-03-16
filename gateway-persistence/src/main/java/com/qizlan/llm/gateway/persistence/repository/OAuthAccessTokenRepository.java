package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.OAuthAccessTokenEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccessTokenRepository extends JpaRepository<OAuthAccessTokenEntity, String> {

    Optional<OAuthAccessTokenEntity> findByRefreshToken(String refreshToken);
}
