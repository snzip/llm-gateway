package com.qizlan.llm.gateway.gateway.security;

import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyRateLimitWindowEntity;
import com.qizlan.llm.gateway.persistence.repository.ApiKeyRateLimitWindowRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyRateLimitService {

    private final ApiKeyRateLimitWindowRepository rateLimitWindowRepository;

    public ApiKeyRateLimitService(ApiKeyRateLimitWindowRepository rateLimitWindowRepository) {
        this.rateLimitWindowRepository = rateLimitWindowRepository;
    }

    @Transactional
    public void assertAllowed(ApiKeyEntity apiKey, IamRuleService.RateLimitPolicy policy) {
        if (apiKey == null || policy == null || policy.requestLimit() <= 0) {
            return;
        }
        long nowSecond = Instant.now().getEpochSecond();
        long windowStart = nowSecond - policy.windowSeconds() + 1;
        int totalInWindow = rateLimitWindowRepository.findByApiKeyIdAndWindowMinuteBetween(apiKey.getId(), windowStart, nowSecond).stream()
                .mapToInt(ApiKeyRateLimitWindowEntity::getRequestCount)
                .sum();
        ApiKeyRateLimitWindowEntity counter = rateLimitWindowRepository.lockByApiKeyIdAndWindowMinute(apiKey.getId(), nowSecond)
                .orElseGet(() -> rateLimitWindowRepository.save(new ApiKeyRateLimitWindowEntity(apiKey.getId(), nowSecond, 0)));
        if (counter.getRequestCount() + 1 > policy.burstLimit()) {
            throw new ApiKeyAccessDeniedException("API key rate limit exceeded");
        }
        if (totalInWindow + 1 > policy.requestLimit()) {
            throw new ApiKeyAccessDeniedException("API key rate limit exceeded");
        }
        counter.increment();
        rateLimitWindowRepository.save(counter);
    }

    @Scheduled(fixedDelayString = "3600000")
    @Transactional
    public void cleanupOldWindows() {
        long cutoff = Instant.now().getEpochSecond() - 7200;
        rateLimitWindowRepository.deleteByWindowMinuteLessThan(cutoff);
    }
}
