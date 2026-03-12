package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.ApiKeyRateLimitWindowEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRateLimitWindowRepository extends JpaRepository<ApiKeyRateLimitWindowEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from ApiKeyRateLimitWindowEntity w where w.apiKeyId = :apiKeyId and w.windowMinute = :windowMinute")
    Optional<ApiKeyRateLimitWindowEntity> lockByApiKeyIdAndWindowMinute(@Param("apiKeyId") String apiKeyId, @Param("windowMinute") long windowMinute);

    long deleteByWindowMinuteLessThan(long windowMinute);
}
