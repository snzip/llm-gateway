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
    public void assertAllowed(ApiKeyEntity apiKey) {
        if (apiKey == null || apiKey.getRequestsPerMinuteLimit() <= 0) {
            return;
        }
        long minuteWindow = Instant.now().getEpochSecond() / 60;
        ApiKeyRateLimitWindowEntity counter = rateLimitWindowRepository.lockByApiKeyIdAndWindowMinute(apiKey.getId(), minuteWindow)
                .orElseGet(() -> rateLimitWindowRepository.save(new ApiKeyRateLimitWindowEntity(apiKey.getId(), minuteWindow, 0)));
        counter.increment();
        rateLimitWindowRepository.save(counter);
        if (counter.getRequestCount() > apiKey.getRequestsPerMinuteLimit()) {
            throw new ApiKeyAccessDeniedException("API key rate limit exceeded");
        }
    }

    @Scheduled(fixedDelayString = "3600000")
    @Transactional
    public void cleanupOldWindows() {
        long cutoff = (Instant.now().getEpochSecond() / 60) - 120;
        rateLimitWindowRepository.deleteByWindowMinuteLessThan(cutoff);
    }
}
