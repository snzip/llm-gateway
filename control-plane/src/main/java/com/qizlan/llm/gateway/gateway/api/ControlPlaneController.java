package com.qizlan.llm.gateway.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.gateway.service.AuditLogService;
import com.qizlan.llm.gateway.gateway.service.ControlPlaneService;
import com.qizlan.llm.gateway.gateway.service.CostAggregationService;
import com.qizlan.llm.gateway.gateway.service.CostAggregationWorkerService;
import com.qizlan.llm.gateway.gateway.service.GuardrailService;
import com.qizlan.llm.gateway.gateway.service.RequestLogQueryService;
import com.qizlan.llm.gateway.gateway.service.RequestContextService;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyIamRuleEntity;
import com.qizlan.llm.gateway.persistence.entity.AuditLogEntity;
import com.qizlan.llm.gateway.persistence.entity.GuardrailRuleEntity;
import com.qizlan.llm.gateway.persistence.entity.GuardrailViolationEntity;
import com.qizlan.llm.gateway.persistence.entity.OrganizationEntity;
import com.qizlan.llm.gateway.persistence.entity.ProjectEntity;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@io.swagger.v3.oas.annotations.tags.Tag(name = "Control Plane", description = "组织/项目/API key/日志/守卫等控制面接口")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "BearerAuth")
public class ControlPlaneController {

    private final ControlPlaneService controlPlaneService;
    private final RequestLogQueryService requestLogQueryService;
    private final CostAggregationService costAggregationService;
    private final CostAggregationWorkerService costAggregationWorkerService;
    private final AuditLogService auditLogService;
    private final GuardrailService guardrailService;
    private final ObjectMapper objectMapper;
    private final RequestContextService requestContextService;

    public ControlPlaneController(
            ControlPlaneService controlPlaneService,
            RequestLogQueryService requestLogQueryService,
            CostAggregationService costAggregationService,
            CostAggregationWorkerService costAggregationWorkerService,
            AuditLogService auditLogService,
            GuardrailService guardrailService,
            ObjectMapper objectMapper,
            RequestContextService requestContextService
    ) {
        this.controlPlaneService = controlPlaneService;
        this.requestLogQueryService = requestLogQueryService;
        this.costAggregationService = costAggregationService;
        this.costAggregationWorkerService = costAggregationWorkerService;
        this.auditLogService = auditLogService;
        this.guardrailService = guardrailService;
        this.objectMapper = objectMapper;
        this.requestContextService = requestContextService;
    }

    @GetMapping("/orgs")
    @Operation(summary = "List organizations", description = "Return all active organizations visible to the control-plane admin.")
    public List<Map<String, Object>> listOrganizations() {
        return controlPlaneService.listOrganizations().stream().map(this::toOrganization).toList();
    }

    @PostMapping("/orgs")
    @Operation(summary = "Create organization", description = "Create an organization entity and write an audit log entry.")
    public Map<String, Object> createOrganization(@RequestBody OrganizationUpsertRequest request, HttpServletRequest httpRequest) {
        return toOrganization(controlPlaneService.createOrganization(requestContextService.get(httpRequest), request.name()));
    }

    @PatchMapping("/orgs/{id}")
    @Operation(summary = "Update organization", description = "Rename an organization.")
    public Map<String, Object> updateOrganization(@PathVariable("id") String id, @RequestBody OrganizationUpsertRequest request, HttpServletRequest httpRequest) {
        return toOrganization(controlPlaneService.updateOrganization(requestContextService.get(httpRequest), id, request.name()));
    }

    @DeleteMapping("/orgs/{id}")
    @Operation(summary = "Deactivate organization", description = "Soft-disable an organization to prevent new activity while retaining historical data.")
    public ResponseEntity<Void> deleteOrganization(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        controlPlaneService.deleteOrganization(requestContextService.get(httpRequest), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects")
    @Operation(summary = "List projects", description = "List all projects across organizations.")
    public List<Map<String, Object>> listProjects() {
        return controlPlaneService.listProjects().stream().map(this::toProject).toList();
    }

    @PostMapping("/projects")
    @Operation(summary = "Create project", description = "Provision a new project under an organization.")
    public Map<String, Object> createProject(@RequestBody ProjectUpsertRequest request, HttpServletRequest httpRequest) {
        return toProject(controlPlaneService.createProject(requestContextService.get(httpRequest), request.organizationId(), request.name()));
    }

    @GetMapping("/projects/{id}")
    @Operation(summary = "Get project", description = "Retrieve project metadata.")
    public Map<String, Object> getProject(@PathVariable("id") String id) {
        return toProject(controlPlaneService.getProject(id));
    }

    @PatchMapping("/projects/{id}")
    @Operation(summary = "Rename project", description = "Update the name of a project.")
    public Map<String, Object> updateProject(@PathVariable("id") String id, @RequestBody ProjectRenameRequest request, HttpServletRequest httpRequest) {
        return toProject(controlPlaneService.updateProject(requestContextService.get(httpRequest), id, request.name()));
    }

    @DeleteMapping("/projects/{id}")
    @Operation(summary = "Deactivate project", description = "Soft-delete project while keeping billing/account history.")
    public ResponseEntity<Void> deleteProject(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        controlPlaneService.deleteProject(requestContextService.get(httpRequest), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/keys/api")
    @Operation(summary = "Create API key", description = "Generate a new API key for a project with audit metadata.")
    public Map<String, Object> createApiKey(@RequestBody ApiKeyCreateRequest request, HttpServletRequest httpRequest) {
        ControlPlaneService.ApiKeyCreateResult result =
                controlPlaneService.createApiKey(requestContextService.get(httpRequest), request.organizationId(), request.projectId(), request.name());
        return Map.of(
                "id", result.entity().getId(),
                "token", result.rawToken(),
                "token_prefix", result.entity().getTokenPrefix(),
                "name", result.entity().getName(),
                "project_id", result.entity().getProject().getId(),
                "organization_id", result.entity().getOrganization().getId()
        );
    }

    @GetMapping("/keys/api")
    @Operation(summary = "List API keys", description = "List API keys across projects with metadata for budget/limits.")
    public List<Map<String, Object>> listApiKeys() {
        return controlPlaneService.listApiKeys().stream().map(this::toApiKey).toList();
    }

    @PatchMapping("/keys/api/{id}")
    @Operation(summary = "Update API key", description = "Adjust key status, budget, and rate limits.")
    public Map<String, Object> updateApiKey(@PathVariable("id") String id, @RequestBody ApiKeyPatchRequest request, HttpServletRequest httpRequest) {
        return toApiKey(controlPlaneService.updateApiKey(
                requestContextService.get(httpRequest),
                id,
                request.name(),
                request.active(),
                request.budget_micros_usd(),
                request.requests_per_minute_limit()));
    }

    @DeleteMapping("/keys/api/{id}")
    @Operation(summary = "Revoke API key", description = "Deactivate an API key while keeping the reference for audit.")
    public ResponseEntity<Void> deleteApiKey(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        controlPlaneService.revokeApiKey(requestContextService.get(httpRequest), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/keys/api/{id}/iam")
    @Operation(summary = "Create IAM rule", description = "Attach an IAM rule (path/model/provider/rate) to an API key.")
    public Map<String, Object> createIamRule(@PathVariable("id") String id, @RequestBody IamRuleCreateRequest request, HttpServletRequest httpRequest) {
        return toIamRule(controlPlaneService.createIamRule(requestContextService.get(httpRequest), id, request.rule_type(), request.effect(), request.pattern()));
    }

    @GetMapping("/keys/api/{id}/iam")
    @Operation(summary = "List IAM rules", description = "Return IAM policy rules associated with the given API key.")
    public List<Map<String, Object>> listIamRules(@PathVariable("id") String id) {
        return controlPlaneService.listIamRules(id).stream().map(this::toIamRule).toList();
    }

    @PatchMapping("/keys/api/{id}/iam/{ruleId}")
    @Operation(summary = "Update IAM rule", description = "Modify an existing IAM rule for an API key.")
    public Map<String, Object> updateIamRule(@PathVariable("id") String id, @PathVariable("ruleId") String ruleId, @RequestBody IamRulePatchRequest request, HttpServletRequest httpRequest) {
        return toIamRule(controlPlaneService.updateIamRule(
                requestContextService.get(httpRequest),
                id,
                ruleId,
                request.rule_type(),
                request.effect(),
                request.pattern(),
                request.active()));
    }

    @DeleteMapping("/keys/api/{id}/iam/{ruleId}")
    @Operation(summary = "Delete IAM rule", description = "Remove an IAM rule from an API key.")
    public ResponseEntity<Void> deleteIamRule(@PathVariable("id") String id, @PathVariable("ruleId") String ruleId, HttpServletRequest httpRequest) {
        controlPlaneService.deleteIamRule(requestContextService.get(httpRequest), id, ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/logs")
    @Operation(summary = "List request logs", description = "Return recent gateway request logs sorted by creation time.")
    public Map<String, Object> listLogs() {
        return Map.of("data", requestLogQueryService.listLogs());
    }

    @GetMapping("/logs/{id}")
    @Operation(summary = "Get log", description = "Fetch a single request log by ID.")
    public Map<String, Object> getLog(@PathVariable("id") String id) {
        return Map.of("data", requestLogQueryService.getLog(id));
    }

    @GetMapping("/costs/summary")
    @Operation(summary = "Summarize costs", description = "Aggregate request costs grouped by provider/model/org/project.")
    public Map<String, Object> costSummary(
            @RequestParam(name = "group_by", required = false) String groupBy,
            @RequestParam(name = "organization_id", required = false) String organizationId,
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "path", required = false) String path
    ) {
        return Map.of("data", costAggregationService.summarize(groupBy, organizationId, projectId, path));
    }

    @GetMapping("/costs/timeseries")
    @Operation(summary = "Cost timeseries", description = "Return cost over time buckets.")
    public Map<String, Object> costTimeseries(
            @RequestParam(name = "bucket", required = false) String bucket,
            @RequestParam(name = "group_by", required = false) String groupBy,
            @RequestParam(name = "organization_id", required = false) String organizationId,
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "path", required = false) String path
    ) {
        return Map.of("data", costAggregationService.timeseries(bucket, groupBy, organizationId, projectId, path));
    }

    @PostMapping("/internal/costs/recompute")
    public Map<String, Object> recomputeCosts(@RequestParam(name = "bucket", required = false) String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return costAggregationWorkerService.recomputeAll();
        }
        return Map.of("rows", costAggregationWorkerService.recompute(bucket));
    }

    @PostMapping("/internal/costs/compact")
    public Map<String, Object> compactCosts(
            @RequestParam(name = "bucket", required = false) String bucket,
            @RequestParam(name = "retention_days", required = false) Long retentionDays
    ) {
        if (bucket == null || bucket.isBlank()) {
            return costAggregationWorkerService.compactAll();
        }
        return Map.of("rows_deleted", costAggregationWorkerService.compact(bucket, retentionDays == null ? 30 : retentionDays));
    }

    @GetMapping("/guardrails/rules/{organizationId}")
    public Map<String, Object> guardrailRules(@PathVariable("organizationId") String organizationId) {
        return Map.of("data", guardrailService.listRules(organizationId).stream().map(this::toGuardrailRule).toList());
    }

    @PostMapping("/guardrails/rules/{organizationId}")
    public Map<String, Object> createGuardrailRule(@PathVariable("organizationId") String organizationId, @RequestBody GuardrailRuleCreateRequest request, HttpServletRequest httpRequest) {
        return toGuardrailRule(guardrailService.createRule(
                requestContextService.get(httpRequest), organizationId, request.name(), request.rule_type(), request.pattern(), request.action()));
    }

    @PatchMapping("/guardrails/rules/{organizationId}/{ruleId}")
    public Map<String, Object> updateGuardrailRule(@PathVariable("organizationId") String organizationId, @PathVariable("ruleId") String ruleId, @RequestBody GuardrailRulePatchRequest request, HttpServletRequest httpRequest) {
        return toGuardrailRule(guardrailService.updateRule(
                requestContextService.get(httpRequest), organizationId, ruleId, request.name(), request.rule_type(), request.pattern(), request.action(), request.active()));
    }

    @DeleteMapping("/guardrails/rules/{organizationId}/{ruleId}")
    public ResponseEntity<Void> deleteGuardrailRule(@PathVariable("organizationId") String organizationId, @PathVariable("ruleId") String ruleId, HttpServletRequest httpRequest) {
        guardrailService.deleteRule(requestContextService.get(httpRequest), organizationId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/guardrails/violations/{organizationId}")
    public Map<String, Object> guardrailViolations(@PathVariable("organizationId") String organizationId) {
        return Map.of("data", guardrailService.listViolations(organizationId).stream().map(this::toGuardrailViolation).toList());
    }

    @GetMapping("/guardrails/stats/{organizationId}")
    public Map<String, Object> guardrailStats(@PathVariable("organizationId") String organizationId) {
        return Map.of("data", guardrailService.stats(organizationId));
    }

    @PostMapping("/guardrails/test/{organizationId}")
    public Map<String, Object> guardrailTest(@PathVariable("organizationId") String organizationId, @RequestBody GuardrailTestRequest request) {
        return guardrailService.test(organizationId, request.text());
    }

    @GetMapping("/guardrails/system-rules")
    public Map<String, Object> guardrailSystemRules() {
        return Map.of("data", List.of(Map.of(
                "rule_type", "KEYWORD",
                "actions", List.of("BLOCK", "LOG"),
                "description", "Case-insensitive keyword match"
        )));
    }

    @GetMapping("/audit-logs/{organizationId}")
    public Map<String, Object> auditLogs(@PathVariable("organizationId") String organizationId) {
        return Map.of("data", auditLogService.list(organizationId).stream().map(this::toAuditLog).toList());
    }

    @GetMapping("/audit-logs/{organizationId}/filters")
    public Map<String, Object> auditLogFilters(@PathVariable("organizationId") String organizationId) {
        return Map.of("data", auditLogService.filters(organizationId));
    }

    private Map<String, Object> toOrganization(OrganizationEntity entity) {
        return Map.of("id", entity.getId(), "name", entity.getName(), "active", entity.isActive());
    }

    private Map<String, Object> toProject(ProjectEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "name", entity.getName(),
                "organization_id", entity.getOrganization().getId(),
                "active", entity.isActive()
        );
    }

    private Map<String, Object> toApiKey(ApiKeyEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "token_prefix", entity.getTokenPrefix(),
                "name", entity.getName(),
                "active", entity.isActive(),
                "organization_id", entity.getOrganization() == null ? "" : entity.getOrganization().getId(),
                "project_id", entity.getProject() == null ? "" : entity.getProject().getId(),
                "spent_micros_usd", entity.getSpentMicrosUsd(),
                "budget_micros_usd", entity.getBudgetMicrosUsd(),
                "requests_per_minute_limit", entity.getRequestsPerMinuteLimit()
        );
    }

    private Map<String, Object> toIamRule(ApiKeyIamRuleEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "api_key_id", entity.getApiKey().getId(),
                "rule_type", entity.getRuleType(),
                "effect", entity.getEffect(),
                "pattern", entity.getPattern(),
                "active", entity.isActive()
        );
    }

    private Map<String, Object> toAuditLog(AuditLogEntity entity) {
        return Map.ofEntries(
                Map.entry("id", entity.getId()),
                Map.entry("organization_id", entity.getOrganizationId()),
                Map.entry("actor_type", entity.getActorType()),
                Map.entry("actor_id", entity.getActorId()),
                Map.entry("correlation_id", entity.getCorrelationId()),
                Map.entry("action", entity.getAction()),
                Map.entry("resource_type", entity.getResourceType()),
                Map.entry("resource_id", entity.getResourceId()),
                Map.entry("parent_resource_type", entity.getParentResourceType() == null ? "" : entity.getParentResourceType()),
                Map.entry("parent_resource_id", entity.getParentResourceId() == null ? "" : entity.getParentResourceId()),
                Map.entry("detail", parseJsonObject(entity.getDetailJson())),
                Map.entry("created_at", entity.getCreatedAt().toString())
        );
    }

    private Map<String, Object> toGuardrailRule(GuardrailRuleEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "organization_id", entity.getOrganizationId(),
                "name", entity.getName(),
                "rule_type", entity.getRuleType(),
                "pattern", entity.getPattern(),
                "action", entity.getAction(),
                "active", entity.isActive()
        );
    }

    private Map<String, Object> toGuardrailViolation(GuardrailViolationEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "organization_id", entity.getOrganizationId(),
                "rule_id", entity.getRuleId(),
                "rule_name", entity.getRuleName(),
                "path", entity.getPath(),
                "action", entity.getAction(),
                "matched_text", entity.getMatchedText(),
                "api_key_id", entity.getApiKeyId() == null ? "" : entity.getApiKeyId(),
                "created_at", entity.getCreatedAt().toString()
        );
    }

    private Object parseJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public record OrganizationUpsertRequest(@NotBlank String name) {
    }

    public record ProjectUpsertRequest(@NotBlank String organizationId, @NotBlank String name) {
    }

    public record ProjectRenameRequest(@NotBlank String name) {
    }

    public record ApiKeyCreateRequest(@NotBlank String organizationId, @NotBlank String projectId, @NotBlank String name) {
    }

    public record ApiKeyPatchRequest(String name, Boolean active, Long budget_micros_usd, Integer requests_per_minute_limit) {
    }

    public record IamRuleCreateRequest(@NotBlank String rule_type, @NotBlank String effect, @NotBlank String pattern) {
    }

    public record IamRulePatchRequest(String rule_type, String effect, String pattern, Boolean active) {
    }

    public record GuardrailRuleCreateRequest(@NotBlank String name, @NotBlank String rule_type, @NotBlank String pattern, @NotBlank String action) {
    }

    public record GuardrailRulePatchRequest(String name, String rule_type, String pattern, String action, Boolean active) {
    }

    public record GuardrailTestRequest(@NotBlank String text) {
    }
}
