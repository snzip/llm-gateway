package com.qizlan.llm.gateway.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.gateway.service.ControlPlaneService;
import com.qizlan.llm.gateway.gateway.service.CostAggregationService;
import com.qizlan.llm.gateway.gateway.service.CostAggregationWorkerService;
import com.qizlan.llm.gateway.gateway.service.ModelSyncService;
import com.qizlan.llm.gateway.gateway.service.RequestLogService;
import com.qizlan.llm.gateway.gateway.service.AuditLogService;
import com.qizlan.llm.gateway.gateway.service.GuardrailService;
import com.qizlan.llm.gateway.gateway.service.RequestContextService;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyIamRuleEntity;
import com.qizlan.llm.gateway.persistence.entity.AuditLogEntity;
import com.qizlan.llm.gateway.persistence.entity.GuardrailRuleEntity;
import com.qizlan.llm.gateway.persistence.entity.GuardrailViolationEntity;
import com.qizlan.llm.gateway.persistence.entity.OrganizationEntity;
import com.qizlan.llm.gateway.persistence.entity.ProjectEntity;
import com.qizlan.llm.gateway.persistence.entity.RequestLogEntity;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping
public class ControlPlaneController {

    private final ControlPlaneService controlPlaneService;
    private final RequestLogService requestLogService;
    private final CostAggregationService costAggregationService;
    private final CostAggregationWorkerService costAggregationWorkerService;
    private final AuditLogService auditLogService;
    private final ModelSyncService modelSyncService;
    private final GuardrailService guardrailService;
    private final ObjectMapper objectMapper;
    private final RequestContextService requestContextService;

    public ControlPlaneController(
            ControlPlaneService controlPlaneService,
            RequestLogService requestLogService,
            CostAggregationService costAggregationService,
            CostAggregationWorkerService costAggregationWorkerService,
            AuditLogService auditLogService,
            ModelSyncService modelSyncService,
            GuardrailService guardrailService,
            ObjectMapper objectMapper,
            RequestContextService requestContextService
    ) {
        this.controlPlaneService = controlPlaneService;
        this.requestLogService = requestLogService;
        this.costAggregationService = costAggregationService;
        this.costAggregationWorkerService = costAggregationWorkerService;
        this.auditLogService = auditLogService;
        this.modelSyncService = modelSyncService;
        this.guardrailService = guardrailService;
        this.objectMapper = objectMapper;
        this.requestContextService = requestContextService;
    }

    @GetMapping("/orgs")
    public Mono<List<Map<String, Object>>> listOrganizations() {
        return blocking(() -> controlPlaneService.listOrganizations().stream().map(this::toOrganization).toList());
    }

    @PostMapping("/orgs")
    public Mono<Map<String, Object>> createOrganization(@RequestBody OrganizationUpsertRequest request, ServerWebExchange exchange) {
        return blocking(() -> toOrganization(controlPlaneService.createOrganization(requestContextService.get(exchange), request.name())));
    }

    @PatchMapping("/orgs/{id}")
    public Mono<Map<String, Object>> updateOrganization(@PathVariable String id, @RequestBody OrganizationUpsertRequest request, ServerWebExchange exchange) {
        return blocking(() -> toOrganization(controlPlaneService.updateOrganization(requestContextService.get(exchange), id, request.name())));
    }

    @DeleteMapping("/orgs/{id}")
    public Mono<ResponseEntity<Void>> deleteOrganization(@PathVariable String id, ServerWebExchange exchange) {
        return blocking(() -> {
            controlPlaneService.deleteOrganization(requestContextService.get(exchange), id);
            return ResponseEntity.noContent().build();
        });
    }

    @GetMapping("/projects")
    public Mono<List<Map<String, Object>>> listProjects() {
        return blocking(() -> controlPlaneService.listProjects().stream().map(this::toProject).toList());
    }

    @PostMapping("/projects")
    public Mono<Map<String, Object>> createProject(@RequestBody ProjectUpsertRequest request, ServerWebExchange exchange) {
        return blocking(() -> toProject(controlPlaneService.createProject(requestContextService.get(exchange), request.organizationId(), request.name())));
    }

    @GetMapping("/projects/{id}")
    public Mono<Map<String, Object>> getProject(@PathVariable String id) {
        return blocking(() -> toProject(controlPlaneService.getProject(id)));
    }

    @PatchMapping("/projects/{id}")
    public Mono<Map<String, Object>> updateProject(@PathVariable String id, @RequestBody ProjectRenameRequest request, ServerWebExchange exchange) {
        return blocking(() -> toProject(controlPlaneService.updateProject(requestContextService.get(exchange), id, request.name())));
    }

    @DeleteMapping("/projects/{id}")
    public Mono<ResponseEntity<Void>> deleteProject(@PathVariable String id, ServerWebExchange exchange) {
        return blocking(() -> {
            controlPlaneService.deleteProject(requestContextService.get(exchange), id);
            return ResponseEntity.noContent().build();
        });
    }

    @PostMapping("/keys/api")
    public Mono<Map<String, Object>> createApiKey(@RequestBody ApiKeyCreateRequest request, ServerWebExchange exchange) {
        return blocking(() -> {
            ControlPlaneService.ApiKeyCreateResult result = controlPlaneService.createApiKey(requestContextService.get(exchange), request.organizationId(), request.projectId(), request.name());
            return Map.of(
                    "id", result.entity().getId(),
                    "token", result.rawToken(),
                    "token_prefix", result.entity().getTokenPrefix(),
                    "name", result.entity().getName(),
                    "project_id", result.entity().getProject().getId(),
                    "organization_id", result.entity().getOrganization().getId()
            );
        });
    }

    @GetMapping("/keys/api")
    public Mono<List<Map<String, Object>>> listApiKeys() {
        return blocking(() -> controlPlaneService.listApiKeys().stream().map(this::toApiKey).toList());
    }

    @PatchMapping("/keys/api/{id}")
    public Mono<Map<String, Object>> updateApiKey(@PathVariable String id, @RequestBody ApiKeyPatchRequest request, ServerWebExchange exchange) {
        return blocking(() -> toApiKey(controlPlaneService.updateApiKey(requestContextService.get(exchange), id, request.name(), request.active(), request.budget_micros_usd(), request.requests_per_minute_limit())));
    }

    @DeleteMapping("/keys/api/{id}")
    public Mono<ResponseEntity<Void>> deleteApiKey(@PathVariable String id, ServerWebExchange exchange) {
        return blocking(() -> {
            controlPlaneService.revokeApiKey(requestContextService.get(exchange), id);
            return ResponseEntity.noContent().build();
        });
    }

    @PostMapping("/keys/api/{id}/iam")
    public Mono<Map<String, Object>> createIamRule(@PathVariable String id, @RequestBody IamRuleCreateRequest request, ServerWebExchange exchange) {
        return blocking(() -> toIamRule(controlPlaneService.createIamRule(requestContextService.get(exchange), id, request.rule_type(), request.effect(), request.pattern())));
    }

    @GetMapping("/keys/api/{id}/iam")
    public Mono<List<Map<String, Object>>> listIamRules(@PathVariable String id) {
        return blocking(() -> controlPlaneService.listIamRules(id).stream().map(this::toIamRule).toList());
    }

    @PatchMapping("/keys/api/{id}/iam/{ruleId}")
    public Mono<Map<String, Object>> updateIamRule(@PathVariable String id, @PathVariable String ruleId, @RequestBody IamRulePatchRequest request, ServerWebExchange exchange) {
        return blocking(() -> toIamRule(controlPlaneService.updateIamRule(requestContextService.get(exchange), id, ruleId, request.rule_type(), request.effect(), request.pattern(), request.active())));
    }

    @DeleteMapping("/keys/api/{id}/iam/{ruleId}")
    public Mono<ResponseEntity<Void>> deleteIamRule(@PathVariable String id, @PathVariable String ruleId, ServerWebExchange exchange) {
        return blocking(() -> {
            controlPlaneService.deleteIamRule(requestContextService.get(exchange), id, ruleId);
            return ResponseEntity.noContent().build();
        });
    }

    @GetMapping("/logs")
    public Mono<List<Map<String, Object>>> listLogs() {
        return blocking(() -> requestLogService.list().stream().map(this::toLog).toList());
    }

    @GetMapping("/logs/{id}")
    public Mono<Map<String, Object>> getLog(@PathVariable String id) {
        return blocking(() -> toLog(requestLogService.get(id)));
    }

    @GetMapping("/costs/summary")
    public Mono<Map<String, Object>> costSummary(
            @RequestParam(name = "group_by", required = false) String groupBy,
            @RequestParam(name = "organization_id", required = false) String organizationId,
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "path", required = false) String path
    ) {
        return blocking(() -> Map.of("data", costAggregationService.summarize(groupBy, organizationId, projectId, path)));
    }

    @GetMapping("/costs/timeseries")
    public Mono<Map<String, Object>> costTimeseries(
            @RequestParam(name = "bucket", required = false) String bucket,
            @RequestParam(name = "group_by", required = false) String groupBy,
            @RequestParam(name = "organization_id", required = false) String organizationId,
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "path", required = false) String path
    ) {
        return blocking(() -> Map.of("data", costAggregationService.timeseries(bucket, groupBy, organizationId, projectId, path)));
    }

    @PostMapping("/internal/costs/recompute")
    public Mono<Map<String, Object>> recomputeCosts(@RequestParam(name = "bucket", required = false) String bucket) {
        return blocking(() -> {
            if (bucket == null || bucket.isBlank()) {
                return costAggregationWorkerService.recomputeAll();
            }
            return Map.of("rows", costAggregationWorkerService.recompute(bucket));
        });
    }

    @PostMapping("/internal/costs/compact")
    public Mono<Map<String, Object>> compactCosts(
            @RequestParam(name = "bucket", required = false) String bucket,
            @RequestParam(name = "retention_days", required = false) Long retentionDays
    ) {
        return blocking(() -> {
            if (bucket == null || bucket.isBlank()) {
                return costAggregationWorkerService.compactAll();
            }
            return Map.of("rows_deleted", costAggregationWorkerService.compact(bucket, retentionDays == null ? 30 : retentionDays));
        });
    }

    @PostMapping("/internal/models/sync")
    public Mono<Map<String, Object>> syncModels(@RequestParam(name = "provider", required = false) String provider) {
        return blocking(() -> {
            if (provider == null || provider.isBlank()) {
                return modelSyncService.syncAll();
            }
            return Map.of("synced_mappings", modelSyncService.syncProvider(provider));
        });
    }

    @GetMapping("/internal/models/sync/history")
    public Mono<Map<String, Object>> syncHistory(@RequestParam(name = "provider", required = false) String provider) {
        return blocking(() -> Map.of("data", modelSyncService.history(provider).stream().map(history -> Map.of(
                "id", history.getId(),
                "provider_id", history.getProviderId(),
                "status", history.getStatus(),
                "discovered_models", history.getDiscoveredModels(),
                "synced_mappings", history.getSyncedMappings(),
                "archived_mappings", history.getArchivedMappings(),
                "detail", history.getDetail(),
                "created_at", history.getCreatedAt()
        )).toList()));
    }

    @GetMapping("/guardrails/rules/{organizationId}")
    public Mono<Map<String, Object>> guardrailRules(@PathVariable String organizationId) {
        return blocking(() -> Map.of("data", guardrailService.listRules(organizationId).stream().map(this::toGuardrailRule).toList()));
    }

    @PostMapping("/guardrails/rules/{organizationId}")
    public Mono<Map<String, Object>> createGuardrailRule(@PathVariable String organizationId, @RequestBody GuardrailRuleCreateRequest request, ServerWebExchange exchange) {
        return blocking(() -> toGuardrailRule(guardrailService.createRule(requestContextService.get(exchange), organizationId, request.name(), request.rule_type(), request.pattern(), request.action())));
    }

    @PatchMapping("/guardrails/rules/{organizationId}/{ruleId}")
    public Mono<Map<String, Object>> updateGuardrailRule(@PathVariable String organizationId, @PathVariable String ruleId, @RequestBody GuardrailRulePatchRequest request, ServerWebExchange exchange) {
        return blocking(() -> toGuardrailRule(guardrailService.updateRule(requestContextService.get(exchange), organizationId, ruleId, request.name(), request.rule_type(), request.pattern(), request.action(), request.active())));
    }

    @DeleteMapping("/guardrails/rules/{organizationId}/{ruleId}")
    public Mono<ResponseEntity<Void>> deleteGuardrailRule(@PathVariable String organizationId, @PathVariable String ruleId, ServerWebExchange exchange) {
        return blocking(() -> {
            guardrailService.deleteRule(requestContextService.get(exchange), organizationId, ruleId);
            return ResponseEntity.noContent().build();
        });
    }

    @GetMapping("/guardrails/violations/{organizationId}")
    public Mono<Map<String, Object>> guardrailViolations(@PathVariable String organizationId) {
        return blocking(() -> Map.of("data", guardrailService.listViolations(organizationId).stream().map(this::toGuardrailViolation).toList()));
    }

    @GetMapping("/guardrails/stats/{organizationId}")
    public Mono<Map<String, Object>> guardrailStats(@PathVariable String organizationId) {
        return blocking(() -> Map.of("data", guardrailService.stats(organizationId)));
    }

    @PostMapping("/guardrails/test/{organizationId}")
    public Mono<Map<String, Object>> guardrailTest(@PathVariable String organizationId, @RequestBody GuardrailTestRequest request) {
        return blocking(() -> guardrailService.test(organizationId, request.text()));
    }

    @GetMapping("/guardrails/system-rules")
    public Mono<Map<String, Object>> guardrailSystemRules() {
        return blocking(() -> Map.of("data", List.of(Map.of(
                "rule_type", "KEYWORD",
                "actions", List.of("BLOCK", "LOG"),
                "description", "Case-insensitive keyword match"
        ))));
    }

    @GetMapping("/audit-logs/{organizationId}")
    public Mono<Map<String, Object>> auditLogs(@PathVariable String organizationId) {
        return blocking(() -> Map.of("data", auditLogService.list(organizationId).stream().map(this::toAuditLog).toList()));
    }

    @GetMapping("/audit-logs/{organizationId}/filters")
    public Mono<Map<String, Object>> auditLogFilters(@PathVariable String organizationId) {
        return blocking(() -> Map.of("data", auditLogService.filters(organizationId)));
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

    private Map<String, Object> toLog(RequestLogEntity entity) {
        return Map.ofEntries(
                Map.entry("id", entity.getId()),
                Map.entry("request_id", entity.getRequestId()),
                Map.entry("path", entity.getPath()),
                Map.entry("requested_model", entity.getRequestedModel()),
                Map.entry("provider_id", entity.getProviderId()),
                Map.entry("http_status", entity.getHttpStatus()),
                Map.entry("latency_ms", entity.getLatencyMs()),
                Map.entry("time_to_first_token_ms", entity.getTimeToFirstTokenMs()),
                Map.entry("prompt_tokens", entity.getPromptTokens()),
                Map.entry("completion_tokens", entity.getCompletionTokens()),
                Map.entry("total_tokens", entity.getTotalTokens()),
                Map.entry("reasoning_tokens", entity.getReasoningTokens()),
                Map.entry("cached_tokens", entity.getCachedTokens()),
                Map.entry("image_count", entity.getImageCount()),
                Map.entry("estimated_cost_micros_usd", entity.getEstimatedCostMicrosUsd()),
                Map.entry("prompt_cost_micros_usd", entity.getPromptCostMicrosUsd()),
                Map.entry("completion_cost_micros_usd", entity.getCompletionCostMicrosUsd()),
                Map.entry("streamed", entity.isStreamed()),
                Map.entry("canceled", entity.isCanceled()),
                Map.entry("retried", entity.isRetried()),
                Map.entry("has_error", entity.isHasError()),
                Map.entry("api_key_id", entity.getApiKeyId() == null ? "" : entity.getApiKeyId()),
                Map.entry("organization_id", entity.getOrganizationId() == null ? "" : entity.getOrganizationId()),
                Map.entry("project_id", entity.getProjectId() == null ? "" : entity.getProjectId()),
                Map.entry("correlation_id", entity.getCorrelationId() == null ? "" : entity.getCorrelationId()),
                Map.entry("trace_id", entity.getTraceId() == null ? "" : entity.getTraceId()),
                Map.entry("span_id", entity.getSpanId() == null ? "" : entity.getSpanId()),
                Map.entry("request_payload", entity.getRequestPayload() == null ? "" : entity.getRequestPayload()),
                Map.entry("response_payload", entity.getResponsePayload() == null ? "" : entity.getResponsePayload()),
                Map.entry("routing_trace", parseRoutingTrace(entity.getRoutingTrace()))
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

    private Object parseRoutingTrace(String routingTrace) {
        if (routingTrace == null || routingTrace.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(routingTrace, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
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

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic());
    }
}
