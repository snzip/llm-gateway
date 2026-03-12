package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.config.GatewayProperties;
import com.qizlan.llm.gateway.persistence.entity.CostAggregateEntity;
import com.qizlan.llm.gateway.persistence.entity.RequestLogEntity;
import com.qizlan.llm.gateway.persistence.repository.CostAggregateRepository;
import com.qizlan.llm.gateway.persistence.repository.RequestLogRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CostAggregationWorkerService {

    private final RequestLogRepository requestLogRepository;
    private final CostAggregateRepository costAggregateRepository;
    private final CostAggregationService costAggregationService;
    private final GatewayProperties properties;

    public CostAggregationWorkerService(
            RequestLogRepository requestLogRepository,
            CostAggregateRepository costAggregateRepository,
            CostAggregationService costAggregationService,
            GatewayProperties properties
    ) {
        this.requestLogRepository = requestLogRepository;
        this.costAggregateRepository = costAggregateRepository;
        this.costAggregationService = costAggregationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${llm.gateway.aggregation.fixed-delay-millis:900000}")
    public void scheduledRecompute() {
        if (properties.aggregation() == null || !properties.aggregation().enabled()) {
            return;
        }
        recomputeAll();
    }

    public Map<String, Object> recomputeAll() {
        int hourly = recompute("hour");
        int daily = recompute("day");
        return Map.of("hour_rows", hourly, "day_rows", daily);
    }

    @Transactional
    public int recompute(String bucketType) {
        String normalizedBucket = normalizeBucket(bucketType);
        List<RequestLogEntity> logs = requestLogRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"));
        Map<String, AggregateAccumulator> grouped = new LinkedHashMap<>();
        for (RequestLogEntity log : logs) {
            OffsetDateTime bucketStart = bucketStart(log.getCreatedAt(), normalizedBucket);
            String key = String.join("|",
                    normalizedBucket,
                    bucketStart.toString(),
                    safe(log.getProviderId()),
                    safe(log.getRequestedModel()),
                    safe(log.getOrganizationId()),
                    safe(log.getProjectId()),
                    safe(log.getPath()));
            grouped.computeIfAbsent(key, ignored -> new AggregateAccumulator(bucketStart, log.getProviderId(), log.getRequestedModel(), log.getOrganizationId(), log.getProjectId(), log.getPath()))
                    .add(log);
        }
        List<CostAggregateEntity> existing = costAggregateRepository.findByBucketType(normalizedBucket, Sort.by(Sort.Direction.ASC, "bucketStart"));
        if (!existing.isEmpty()) {
            costAggregateRepository.deleteAll(existing);
        }
        costAggregateRepository.saveAll(grouped.values().stream()
                .map(accumulator -> accumulator.toEntity(normalizedBucket))
                .toList());
        costAggregationService.invalidateCache();
        return grouped.size();
    }

    private OffsetDateTime bucketStart(OffsetDateTime createdAt, String bucketType) {
        OffsetDateTime safe = createdAt == null ? OffsetDateTime.now() : createdAt;
        return switch (bucketType) {
            case "hour" -> safe.truncatedTo(ChronoUnit.HOURS);
            case "day" -> safe.truncatedTo(ChronoUnit.DAYS);
            default -> throw new IllegalArgumentException("Unsupported bucket: " + bucketType);
        };
    }

    private String normalizeBucket(String bucketType) {
        return bucketType == null || bucketType.isBlank() ? "day" : bucketType.toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class AggregateAccumulator {
        private final OffsetDateTime bucketStart;
        private final String providerId;
        private final String requestedModel;
        private final String organizationId;
        private final String projectId;
        private final String path;
        private long requestCount;
        private long estimatedCostMicrosUsd;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;

        private AggregateAccumulator(OffsetDateTime bucketStart, String providerId, String requestedModel, String organizationId, String projectId, String path) {
            this.bucketStart = bucketStart;
            this.providerId = providerId;
            this.requestedModel = requestedModel;
            this.organizationId = organizationId;
            this.projectId = projectId;
            this.path = path;
        }

        private void add(RequestLogEntity log) {
            requestCount++;
            estimatedCostMicrosUsd += log.getEstimatedCostMicrosUsd();
            promptTokens += log.getPromptTokens();
            completionTokens += log.getCompletionTokens();
            totalTokens += log.getTotalTokens();
        }

        private CostAggregateEntity toEntity(String bucketType) {
            return new CostAggregateEntity(
                    bucketType,
                    bucketStart,
                    providerId == null || providerId.isBlank() ? "unknown" : providerId,
                    requestedModel == null || requestedModel.isBlank() ? "unknown" : requestedModel,
                    organizationId,
                    projectId,
                    path == null || path.isBlank() ? "unknown" : path,
                    requestCount,
                    estimatedCostMicrosUsd,
                    promptTokens,
                    completionTokens,
                    totalTokens
            );
        }
    }
}
