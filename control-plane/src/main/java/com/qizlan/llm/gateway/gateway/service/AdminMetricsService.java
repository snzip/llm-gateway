package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.OrganizationEntity;
import com.qizlan.llm.gateway.persistence.entity.ProjectEntity;
import com.qizlan.llm.gateway.persistence.entity.RequestLogEntity;
import com.qizlan.llm.gateway.persistence.repository.ApiKeyRepository;
import com.qizlan.llm.gateway.persistence.repository.OrganizationRepository;
import com.qizlan.llm.gateway.persistence.repository.ProjectRepository;
import com.qizlan.llm.gateway.persistence.repository.RequestLogRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AdminMetricsService {

    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final RequestLogRepository requestLogRepository;

    public AdminMetricsService(
            OrganizationRepository organizationRepository,
            ProjectRepository projectRepository,
            ApiKeyRepository apiKeyRepository,
            RequestLogRepository requestLogRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.projectRepository = projectRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.requestLogRepository = requestLogRepository;
    }

    public Map<String, Object> overview() {
        List<RequestLogEntity> logs = requestLogRepository.findAll();
        long totalCost = logs.stream().mapToLong(RequestLogEntity::getEstimatedCostMicrosUsd).sum();
        return Map.of(
                "organization_count", organizationRepository.count(),
                "project_count", projectRepository.count(),
                "api_key_count", apiKeyRepository.count(),
                "request_count", logs.size(),
                "estimated_cost_micros_usd", totalCost
        );
    }

    public List<Map<String, Object>> organizations() {
        List<ProjectEntity> projects = projectRepository.findAll();
        List<ApiKeyEntity> apiKeys = apiKeyRepository.findAll();
        return organizationRepository.findAll().stream()
                .map(org -> Map.<String, Object>of(
                        "id", org.getId(),
                        "name", org.getName(),
                        "active", org.isActive(),
                        "project_count", projects.stream().filter(project -> project.getOrganization().getId().equals(org.getId())).count(),
                        "api_key_count", apiKeys.stream().filter(key -> key.getOrganization() != null && key.getOrganization().getId().equals(org.getId())).count()
                ))
                .sorted(Comparator.comparing(item -> item.get("name").toString()))
                .toList();
    }

    public Map<String, Object> organizationDetail(String organizationId) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization: " + organizationId));
        long requestCount = requestLogRepository.findAll().stream()
                .filter(log -> organizationId.equals(log.getOrganizationId()))
                .count();
        long cost = requestLogRepository.findAll().stream()
                .filter(log -> organizationId.equals(log.getOrganizationId()))
                .mapToLong(RequestLogEntity::getEstimatedCostMicrosUsd)
                .sum();
        return Map.of(
                "id", organization.getId(),
                "name", organization.getName(),
                "active", organization.isActive(),
                "request_count", requestCount,
                "estimated_cost_micros_usd", cost
        );
    }

    public List<Map<String, Object>> organizationProjects(String organizationId) {
        return projectRepository.findAll().stream()
                .filter(project -> project.getOrganization().getId().equals(organizationId))
                .map(project -> Map.<String, Object>of(
                        "id", project.getId(),
                        "name", project.getName(),
                        "active", project.isActive(),
                        "organization_id", organizationId
                ))
                .sorted(Comparator.comparing(item -> item.get("name").toString()))
                .toList();
    }

    public Map<String, Object> projectMetrics(String organizationId, String projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project: " + projectId));
        if (!project.getOrganization().getId().equals(organizationId)) {
            throw new IllegalArgumentException("Project does not belong to organization: " + organizationId);
        }
        List<RequestLogEntity> logs = requestLogRepository.findAll().stream()
                .filter(log -> organizationId.equals(log.getOrganizationId()))
                .filter(log -> projectId.equals(log.getProjectId()))
                .toList();
        return Map.of(
                "project_id", projectId,
                "organization_id", organizationId,
                "request_count", logs.size(),
                "estimated_cost_micros_usd", logs.stream().mapToLong(RequestLogEntity::getEstimatedCostMicrosUsd).sum(),
                "models", logs.stream().map(RequestLogEntity::getRequestedModel).distinct().sorted().toList(),
                "providers", logs.stream().map(RequestLogEntity::getProviderId).distinct().sorted().toList()
        );
    }

    public List<Map<String, Object>> projectLogs(String organizationId, String projectId) {
        return requestLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(log -> organizationId.equals(log.getOrganizationId()))
                .filter(log -> projectId.equals(log.getProjectId()))
                .map(log -> Map.<String, Object>of(
                        "id", log.getId(),
                        "path", log.getPath(),
                        "requested_model", log.getRequestedModel(),
                        "provider_id", log.getProviderId(),
                        "http_status", log.getHttpStatus(),
                        "estimated_cost_micros_usd", log.getEstimatedCostMicrosUsd(),
                        "created_at", log.getCreatedAt()
                ))
                .toList();
    }

    public List<Map<String, Object>> timeseries(String bucket) {
        String normalizedBucket = bucket == null || bucket.isBlank() ? "day" : bucket.toLowerCase();
        return requestLogRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt")).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        log -> timeBucket(log, normalizedBucket),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "bucket", entry.getKey(),
                        "request_count", entry.getValue().size(),
                        "estimated_cost_micros_usd", entry.getValue().stream().mapToLong(RequestLogEntity::getEstimatedCostMicrosUsd).sum()
                ))
                .toList();
    }

    public List<Map<String, Object>> costByModel() {
        return requestLogRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        log -> log.getRequestedModel() == null || log.getRequestedModel().isBlank() ? "unknown" : log.getRequestedModel(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "group_by", "model",
                        "group_value", entry.getKey(),
                        "request_count", entry.getValue().size(),
                        "estimated_cost_micros_usd", entry.getValue().stream().mapToLong(RequestLogEntity::getEstimatedCostMicrosUsd).sum(),
                        "prompt_tokens", entry.getValue().stream().mapToInt(RequestLogEntity::getPromptTokens).sum(),
                        "completion_tokens", entry.getValue().stream().mapToInt(RequestLogEntity::getCompletionTokens).sum(),
                        "total_tokens", entry.getValue().stream().mapToInt(RequestLogEntity::getTotalTokens).sum()
                ))
                .sorted(Comparator.comparingLong((Map<String, Object> item) -> ((Number) item.get("estimated_cost_micros_usd")).longValue()).reversed())
                .toList();
    }

    private String timeBucket(RequestLogEntity log, String bucket) {
        if (log.getCreatedAt() == null) {
            return "unknown";
        }
        return switch (bucket) {
            case "hour" -> log.getCreatedAt().withMinute(0).withSecond(0).withNano(0).toString();
            case "day" -> log.getCreatedAt().toLocalDate().toString();
            default -> log.getCreatedAt().toLocalDate().toString();
        };
    }
}
