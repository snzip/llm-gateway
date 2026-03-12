package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.RequestLogEntity;
import com.qizlan.llm.gateway.persistence.entity.CostAggregateEntity;
import com.qizlan.llm.gateway.persistence.repository.CostAggregateRepository;
import com.qizlan.llm.gateway.persistence.repository.RequestLogRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.qizlan.llm.gateway.config.GatewayProperties;

@Service
public class CostAggregationService {

    private final RequestLogRepository requestLogRepository;
    private final CostAggregateRepository costAggregateRepository;
    private final GatewayProperties properties;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CostAggregationService(RequestLogRepository requestLogRepository, CostAggregateRepository costAggregateRepository, GatewayProperties properties) {
        this.requestLogRepository = requestLogRepository;
        this.costAggregateRepository = costAggregateRepository;
        this.properties = properties;
    }

    public List<Map<String, Object>> summarize(String groupBy, String organizationId, String projectId, String path) {
        String normalizedGroupBy = normalizeGroupBy(groupBy);
        return cached("summary|" + normalizedGroupBy + "|" + safe(organizationId) + "|" + safe(projectId) + "|" + safe(path), () ->
                aggregateRows("day", organizationId, projectId, path).stream()
                        .collect(Collectors.groupingBy(row -> groupKey(row, normalizedGroupBy), LinkedHashMap::new, Collectors.toList()))
                        .entrySet().stream()
                        .map(entry -> summarizeAggregateGroup(normalizedGroupBy, entry.getKey(), entry.getValue()))
                        .sorted(Comparator.comparingLong((Map<String, Object> item) -> ((Number) item.get("estimated_cost_micros_usd")).longValue()).reversed())
                        .toList());
    }

    public List<Map<String, Object>> timeseries(String bucket, String groupBy, String organizationId, String projectId, String path) {
        String normalizedBucket = normalizeBucket(bucket);
        String normalizedGroupBy = normalizeGroupBy(groupBy);
        return cached("timeseries|" + normalizedBucket + "|" + normalizedGroupBy + "|" + safe(organizationId) + "|" + safe(projectId) + "|" + safe(path), () ->
                aggregateRows(normalizedBucket, organizationId, projectId, path).stream()
                        .collect(Collectors.groupingBy(
                                row -> timeKey(row.getBucketStart(), normalizedBucket) + "|" + groupKey(row, normalizedGroupBy),
                                LinkedHashMap::new,
                                Collectors.toList()))
                        .entrySet().stream()
                        .map(entry -> {
                            String[] parts = entry.getKey().split("\\|", 2);
                            Map<String, Object> summary = summarizeAggregateGroup(normalizedGroupBy, parts[1], entry.getValue());
                            return Map.ofEntries(
                                    Map.entry("bucket", parts[0]),
                                    Map.entry("group_by", normalizedGroupBy),
                                    Map.entry("group_value", parts[1]),
                                    Map.entry("request_count", summary.get("request_count")),
                                    Map.entry("estimated_cost_micros_usd", summary.get("estimated_cost_micros_usd")),
                                    Map.entry("prompt_tokens", summary.get("prompt_tokens")),
                                    Map.entry("completion_tokens", summary.get("completion_tokens")),
                                    Map.entry("total_tokens", summary.get("total_tokens"))
                            );
                        })
                        .sorted(Comparator
                                .comparing((Map<String, Object> item) -> item.get("bucket").toString())
                                .thenComparing(item -> item.get("group_value").toString()))
                        .toList());
    }

    public void invalidateCache() {
        cache.clear();
    }

    private List<RequestLogEntity> filteredLogs(String organizationId, String projectId, String path) {
        return requestLogRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt")).stream()
                .filter(log -> organizationId == null || organizationId.isBlank() || Objects.equals(organizationId, log.getOrganizationId()))
                .filter(log -> projectId == null || projectId.isBlank() || Objects.equals(projectId, log.getProjectId()))
                .filter(log -> path == null || path.isBlank() || Objects.equals(path, log.getPath()))
                .toList();
    }

    private List<CostAggregateEntity> aggregateRows(String bucket, String organizationId, String projectId, String path) {
        String normalizedBucket = normalizeBucket(bucket);
        List<CostAggregateEntity> aggregates = costAggregateRepository.findByBucketType(normalizedBucket, Sort.by(Sort.Direction.ASC, "bucketStart"));
        if (!aggregates.isEmpty() && aggregatesAreFresh(normalizedBucket)) {
            return aggregates.stream()
                    .filter(row -> organizationId == null || organizationId.isBlank() || Objects.equals(organizationId, row.getOrganizationId()))
                    .filter(row -> projectId == null || projectId.isBlank() || Objects.equals(projectId, row.getProjectId()))
                    .filter(row -> path == null || path.isBlank() || Objects.equals(path, row.getPath()))
                    .toList();
        }
        return filteredLogs(organizationId, projectId, path).stream()
                .map(log -> new CostAggregateEntity(
                        normalizedBucket,
                        bucketStart(log.getCreatedAt(), normalizedBucket),
                        emptyToUnknown(log.getProviderId()),
                        emptyToUnknown(log.getRequestedModel()),
                        log.getOrganizationId(),
                        log.getProjectId(),
                        emptyToUnknown(log.getPath()),
                        1,
                        log.getEstimatedCostMicrosUsd(),
                        log.getPromptTokens(),
                        log.getCompletionTokens(),
                        log.getTotalTokens()))
                .toList();
    }

    private boolean aggregatesAreFresh(String bucketType) {
        RequestLogEntity latestLog = requestLogRepository.findTopByOrderByCreatedAtDesc();
        if (latestLog == null) {
            return true;
        }
        CostAggregateEntity latestAggregate = costAggregateRepository.findTopByBucketTypeOrderByUpdatedAtDesc(bucketType);
        return latestAggregate != null && !latestAggregate.getUpdatedAt().isBefore(latestLog.getCreatedAt());
    }

    private Map<String, Object> summarizeAggregateGroup(String groupBy, String key, List<CostAggregateEntity> rows) {
        long requestCount = rows.stream().mapToLong(CostAggregateEntity::getRequestCount).sum();
        long estimatedCost = rows.stream().mapToLong(CostAggregateEntity::getEstimatedCostMicrosUsd).sum();
        int promptTokens = rows.stream().mapToInt(CostAggregateEntity::getPromptTokens).sum();
        int completionTokens = rows.stream().mapToInt(CostAggregateEntity::getCompletionTokens).sum();
        int totalTokens = rows.stream().mapToInt(CostAggregateEntity::getTotalTokens).sum();
        return Map.ofEntries(
                Map.entry("group_by", groupBy),
                Map.entry("group_value", key),
                Map.entry("request_count", requestCount),
                Map.entry("estimated_cost_micros_usd", estimatedCost),
                Map.entry("prompt_tokens", promptTokens),
                Map.entry("completion_tokens", completionTokens),
                Map.entry("total_tokens", totalTokens)
        );
    }

    private String groupKey(CostAggregateEntity row, String groupBy) {
        return switch (normalizeGroupBy(groupBy)) {
            case "provider" -> emptyToUnknown(row.getProviderId());
            case "model" -> emptyToUnknown(row.getRequestedModel());
            case "project" -> emptyToUnknown(row.getProjectId());
            case "organization" -> emptyToUnknown(row.getOrganizationId());
            default -> throw new IllegalArgumentException("Unsupported group_by: " + groupBy);
        };
    }

    private String timeKey(OffsetDateTime timestamp, String bucket) {
        OffsetDateTime safe = timestamp == null ? OffsetDateTime.now() : timestamp;
        return switch (normalizeBucket(bucket)) {
            case "hour" -> safe.truncatedTo(ChronoUnit.HOURS).toString();
            case "day" -> safe.truncatedTo(ChronoUnit.DAYS).toLocalDate().toString();
            default -> throw new IllegalArgumentException("Unsupported bucket: " + bucket);
        };
    }

    private String normalizeGroupBy(String groupBy) {
        return groupBy == null || groupBy.isBlank() ? "provider" : groupBy.toLowerCase();
    }

    private String normalizeBucket(String bucket) {
        return bucket == null || bucket.isBlank() ? "day" : bucket.toLowerCase();
    }

    private OffsetDateTime bucketStart(OffsetDateTime timestamp, String bucket) {
        OffsetDateTime safe = timestamp == null ? OffsetDateTime.now() : timestamp;
        return switch (bucket) {
            case "hour" -> safe.truncatedTo(ChronoUnit.HOURS);
            case "day" -> safe.truncatedTo(ChronoUnit.DAYS);
            default -> throw new IllegalArgumentException("Unsupported bucket: " + bucket);
        };
    }

    private String emptyToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<Map<String, Object>> cached(String key, java.util.function.Supplier<List<Map<String, Object>>> supplier) {
        long now = Instant.now().toEpochMilli();
        long ttlMillis = properties.aggregation() == null ? 0L : properties.aggregation().cacheTtlMillis();
        if (ttlMillis <= 0L) {
            return supplier.get();
        }
        CacheEntry existing = cache.get(key);
        if (existing != null && now - existing.createdAtMillis() <= ttlMillis) {
            return existing.payload();
        }
        List<Map<String, Object>> payload = supplier.get();
        cache.put(key, new CacheEntry(now, payload));
        return payload;
    }

    private record CacheEntry(long createdAtMillis, List<Map<String, Object>> payload) {
    }
}
