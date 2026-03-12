package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.gateway.security.ApiKeyTokenService;
import com.qizlan.llm.gateway.gateway.security.ApiKeyLookupCache;
import com.qizlan.llm.gateway.gateway.security.IamRuleService;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyIamRuleEntity;
import com.qizlan.llm.gateway.persistence.entity.OrganizationEntity;
import com.qizlan.llm.gateway.persistence.entity.ProjectEntity;
import com.qizlan.llm.gateway.persistence.repository.ApiKeyIamRuleRepository;
import com.qizlan.llm.gateway.persistence.repository.ApiKeyRepository;
import com.qizlan.llm.gateway.persistence.repository.OrganizationRepository;
import com.qizlan.llm.gateway.persistence.repository.ProjectRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ControlPlaneService {

    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyIamRuleRepository apiKeyIamRuleRepository;
    private final ApiKeyTokenService tokenService;
    private final AuditLogService auditLogService;
    private final ApiKeyLookupCache apiKeyLookupCache;
    private final IamRuleService iamRuleService;

    public ControlPlaneService(
            OrganizationRepository organizationRepository,
            ProjectRepository projectRepository,
            ApiKeyRepository apiKeyRepository,
            ApiKeyIamRuleRepository apiKeyIamRuleRepository,
            ApiKeyTokenService tokenService,
            AuditLogService auditLogService,
            ApiKeyLookupCache apiKeyLookupCache,
            IamRuleService iamRuleService
    ) {
        this.organizationRepository = organizationRepository;
        this.projectRepository = projectRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyIamRuleRepository = apiKeyIamRuleRepository;
        this.tokenService = tokenService;
        this.auditLogService = auditLogService;
        this.apiKeyLookupCache = apiKeyLookupCache;
        this.iamRuleService = iamRuleService;
    }

    public List<OrganizationEntity> listOrganizations() {
        return organizationRepository.findAll();
    }

    public OrganizationEntity createOrganization(RequestContext context, String name) {
        OrganizationEntity entity = organizationRepository.save(new OrganizationEntity(name));
        auditLogService.record(context, entity.getId(), "organization.create", "organization", entity.getId(), null, null, Map.of(
                "name", entity.getName(),
                "active", entity.isActive()
        ));
        return entity;
    }

    public OrganizationEntity updateOrganization(RequestContext context, String id, String name) {
        OrganizationEntity entity = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization: " + id));
        entity.setName(name);
        OrganizationEntity saved = organizationRepository.save(entity);
        auditLogService.record(context, saved.getId(), "organization.update", "organization", saved.getId(), null, null, Map.of(
                "name", saved.getName(),
                "active", saved.isActive()
        ));
        return saved;
    }

    public void deleteOrganization(RequestContext context, String id) {
        OrganizationEntity entity = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization: " + id));
        entity.setActive(false);
        organizationRepository.save(entity);
        auditLogService.record(context, entity.getId(), "organization.delete", "organization", entity.getId(), null, null, Map.of(
                "name", entity.getName(),
                "active", entity.isActive()
        ));
    }

    public ProjectEntity createProject(RequestContext context, String organizationId, String name) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization: " + organizationId));
        ProjectEntity entity = projectRepository.save(new ProjectEntity(name, organization));
        auditLogService.record(context, organization.getId(), "project.create", "project", entity.getId(), "organization", organization.getId(), Map.of(
                "name", entity.getName(),
                "organization_id", organization.getId(),
                "active", entity.isActive()
        ));
        return entity;
    }

    public List<ProjectEntity> listProjects() {
        return projectRepository.findAll();
    }

    public ProjectEntity getProject(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project: " + id));
    }

    public ProjectEntity updateProject(RequestContext context, String id, String name) {
        ProjectEntity entity = getProject(id);
        entity.setName(name);
        ProjectEntity saved = projectRepository.save(entity);
        auditLogService.record(context, saved.getOrganization().getId(), "project.update", "project", saved.getId(), "organization", saved.getOrganization().getId(), Map.of(
                "name", saved.getName(),
                "organization_id", saved.getOrganization().getId(),
                "active", saved.isActive()
        ));
        return saved;
    }

    public void deleteProject(RequestContext context, String id) {
        ProjectEntity entity = getProject(id);
        entity.setActive(false);
        projectRepository.save(entity);
        auditLogService.record(context, entity.getOrganization().getId(), "project.delete", "project", entity.getId(), "organization", entity.getOrganization().getId(), Map.of(
                "name", entity.getName(),
                "organization_id", entity.getOrganization().getId(),
                "active", entity.isActive()
        ));
    }

    public ApiKeyCreateResult createApiKey(RequestContext context, String organizationId, String projectId, String name) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization: " + organizationId));
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project: " + projectId));
        String rawToken = tokenService.generateRawToken();
        ApiKeyEntity entity = new ApiKeyEntity(
                tokenService.hash(rawToken),
                tokenService.prefix(rawToken),
                name,
                true,
                organization,
                project
        );
        ApiKeyEntity saved = apiKeyRepository.save(entity);
        apiKeyLookupCache.evict(saved);
        auditLogService.record(context, organization.getId(), "api_key.create", "api_key", entity.getId(), "project", project.getId(), Map.of(
                "name", saved.getName(),
                "project_id", project.getId(),
                "token_prefix", saved.getTokenPrefix(),
                "active", saved.isActive()
        ));
        return new ApiKeyCreateResult(saved, rawToken);
    }

    public List<ApiKeyEntity> listApiKeys() {
        return apiKeyRepository.findAll();
    }

    public ApiKeyEntity updateApiKey(RequestContext context, String id, String name, Boolean active, Long budgetMicrosUsd) {
        return updateApiKey(context, id, name, active, budgetMicrosUsd, null);
    }

    public ApiKeyEntity updateApiKey(RequestContext context, String id, String name, Boolean active, Long budgetMicrosUsd, Integer requestsPerMinuteLimit) {
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown api key: " + id));
        if (name != null && !name.isBlank()) {
            entity.setName(name);
        }
        if (active != null) {
            entity.setActive(active);
        }
        if (budgetMicrosUsd != null) {
            entity.setBudgetMicrosUsd(budgetMicrosUsd);
        }
        if (requestsPerMinuteLimit != null) {
            entity.setRequestsPerMinuteLimit(requestsPerMinuteLimit);
        }
        ApiKeyEntity saved = apiKeyRepository.save(entity);
        apiKeyLookupCache.evict(saved);
        auditLogService.record(context, saved.getOrganization() == null ? "" : saved.getOrganization().getId(), "api_key.update", "api_key", saved.getId(), "project", saved.getProject() == null ? "" : saved.getProject().getId(), Map.of(
                "name", saved.getName(),
                "project_id", saved.getProject() == null ? "" : saved.getProject().getId(),
                "token_prefix", saved.getTokenPrefix(),
                "active", saved.isActive(),
                "budget_micros_usd", saved.getBudgetMicrosUsd(),
                "requests_per_minute_limit", saved.getRequestsPerMinuteLimit()
        ));
        return saved;
    }

    public void revokeApiKey(RequestContext context, String id) {
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown api key: " + id));
        entity.setActive(false);
        ApiKeyEntity saved = apiKeyRepository.save(entity);
        apiKeyLookupCache.evict(saved);
        auditLogService.record(context, entity.getOrganization() == null ? "" : entity.getOrganization().getId(), "api_key.delete", "api_key", entity.getId(), "project", entity.getProject() == null ? "" : entity.getProject().getId(), Map.of(
                "name", entity.getName(),
                "project_id", entity.getProject() == null ? "" : entity.getProject().getId(),
                "token_prefix", entity.getTokenPrefix(),
                "active", entity.isActive()
        ));
    }

    public ApiKeyIamRuleEntity createIamRule(RequestContext context, String apiKeyId, String ruleType, String effect, String pattern) {
        ApiKeyEntity apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown api key: " + apiKeyId));
        String organizationId = apiKey.getOrganization() == null ? "" : apiKey.getOrganization().getId();
        ApiKeyIamRuleEntity entity = new ApiKeyIamRuleEntity(apiKey, normalizeRuleType(ruleType), normalizeEffect(effect), pattern, true);
        ApiKeyIamRuleEntity saved = apiKeyIamRuleRepository.save(entity);
        iamRuleService.evictRules(apiKey.getId());
        auditLogService.record(context, organizationId, "iam_rule.create", "iam_rule", saved.getId(), "api_key", apiKey.getId(), Map.of(
                "api_key_id", apiKey.getId(),
                "rule_type", saved.getRuleType(),
                "effect", saved.getEffect(),
                "pattern", saved.getPattern(),
                "active", saved.isActive()
        ));
        return apiKeyIamRuleRepository.findById(saved.getId()).orElse(saved);
    }

    public List<ApiKeyIamRuleEntity> listIamRules(String apiKeyId) {
        ensureApiKeyExists(apiKeyId);
        return apiKeyIamRuleRepository.findByApiKeyIdOrderByRuleTypeAscEffectAscPatternAsc(apiKeyId);
    }

    public ApiKeyIamRuleEntity updateIamRule(RequestContext context, String apiKeyId, String ruleId, String ruleType, String effect, String pattern, Boolean active) {
        ensureApiKeyExists(apiKeyId);
        ApiKeyIamRuleEntity entity = apiKeyIamRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown IAM rule: " + ruleId));
        if (!entity.getApiKey().getId().equals(apiKeyId)) {
            throw new IllegalArgumentException("IAM rule does not belong to API key: " + apiKeyId);
        }
        String organizationId = entity.getApiKey().getOrganization() == null ? "" : entity.getApiKey().getOrganization().getId();
        String boundApiKeyId = entity.getApiKey().getId();
        if (ruleType != null && !ruleType.isBlank()) {
            entity.setRuleType(normalizeRuleType(ruleType));
        }
        if (effect != null && !effect.isBlank()) {
            entity.setEffect(normalizeEffect(effect));
        }
        if (pattern != null && !pattern.isBlank()) {
            entity.setPattern(pattern);
        }
        if (active != null) {
            entity.setActive(active);
        }
        ApiKeyIamRuleEntity saved = apiKeyIamRuleRepository.save(entity);
        iamRuleService.evictRules(boundApiKeyId);
        auditLogService.record(context, organizationId, "iam_rule.update", "iam_rule", saved.getId(), "api_key", boundApiKeyId, Map.of(
                "api_key_id", boundApiKeyId,
                "rule_type", saved.getRuleType(),
                "effect", saved.getEffect(),
                "pattern", saved.getPattern(),
                "active", saved.isActive()
        ));
        return apiKeyIamRuleRepository.findById(saved.getId()).orElse(saved);
    }

    public void deleteIamRule(RequestContext context, String apiKeyId, String ruleId) {
        ensureApiKeyExists(apiKeyId);
        ApiKeyIamRuleEntity entity = apiKeyIamRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown IAM rule: " + ruleId));
        if (!entity.getApiKey().getId().equals(apiKeyId)) {
            throw new IllegalArgumentException("IAM rule does not belong to API key: " + apiKeyId);
        }
        String organizationId = entity.getApiKey().getOrganization() == null ? "" : entity.getApiKey().getOrganization().getId();
        String boundApiKeyId = entity.getApiKey().getId();
        auditLogService.record(context, organizationId, "iam_rule.delete", "iam_rule", entity.getId(), "api_key", boundApiKeyId, Map.of(
                "api_key_id", boundApiKeyId,
                "rule_type", entity.getRuleType(),
                "effect", entity.getEffect(),
                "pattern", entity.getPattern(),
                "active", entity.isActive()
        ));
        apiKeyIamRuleRepository.delete(entity);
        iamRuleService.evictRules(boundApiKeyId);
    }

    private void ensureApiKeyExists(String apiKeyId) {
        if (!apiKeyRepository.existsById(apiKeyId)) {
            throw new IllegalArgumentException("Unknown api key: " + apiKeyId);
        }
    }

    private String normalizeRuleType(String value) {
        String normalized = value.toUpperCase();
        if (!List.of("PATH", "MODEL", "PROVIDER", "RATE").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported IAM rule type: " + value);
        }
        return normalized;
    }

    private String normalizeEffect(String value) {
        String normalized = value.toUpperCase();
        if (!List.of("ALLOW", "DENY", "LIMIT").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported IAM rule effect: " + value);
        }
        return normalized;
    }

    public record ApiKeyCreateResult(ApiKeyEntity entity, String rawToken) {
    }
}
