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
    private final CostAggregationService costAggregationService;

    public AdminMetricsService(
            OrganizationRepository organizationRepository,
            ProjectRepository projectRepository,
            ApiKeyRepository apiKeyRepository,
            RequestLogRepository requestLogRepository,
            CostAggregationService costAggregationService
    ) {
        this.organizationRepository = organizationRepository;
        this.projectRepository = projectRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.requestLogRepository = requestLogRepository;
        this.costAggregationService = costAggregationService;
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
        return costAggregationService.timeseries(bucket, "organization", null, null, null);
    }

    public List<Map<String, Object>> costByModel() {
        return costAggregationService.summarize("model", null, null, null);
    }
}
