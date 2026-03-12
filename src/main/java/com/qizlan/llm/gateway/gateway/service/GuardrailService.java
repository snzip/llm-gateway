package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.gateway.security.GuardrailViolationException;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.GuardrailRuleEntity;
import com.qizlan.llm.gateway.persistence.entity.GuardrailViolationEntity;
import com.qizlan.llm.gateway.persistence.repository.GuardrailRuleRepository;
import com.qizlan.llm.gateway.persistence.repository.GuardrailViolationRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class GuardrailService {

    private final GuardrailRuleRepository guardrailRuleRepository;
    private final GuardrailViolationRepository guardrailViolationRepository;
    private final AuditLogService auditLogService;

    public GuardrailService(GuardrailRuleRepository guardrailRuleRepository, GuardrailViolationRepository guardrailViolationRepository, AuditLogService auditLogService) {
        this.guardrailRuleRepository = guardrailRuleRepository;
        this.guardrailViolationRepository = guardrailViolationRepository;
        this.auditLogService = auditLogService;
    }

    public List<GuardrailRuleEntity> listRules(String organizationId) {
        return guardrailRuleRepository.findByOrganizationId(organizationId, Sort.by(Sort.Direction.ASC, "name"));
    }

    public GuardrailRuleEntity createRule(String organizationId, String name, String ruleType, String pattern, String action) {
        GuardrailRuleEntity entity = new GuardrailRuleEntity(organizationId, name, normalizeType(ruleType), pattern, normalizeAction(action), true);
        GuardrailRuleEntity saved = guardrailRuleRepository.save(entity);
        auditLogService.record(organizationId, "guardrail_rule.create", "guardrail_rule", saved.getId(), Map.of(
                "name", saved.getName(),
                "rule_type", saved.getRuleType(),
                "pattern", saved.getPattern(),
                "action", saved.getAction(),
                "active", saved.isActive()
        ));
        return saved;
    }

    public GuardrailRuleEntity updateRule(String organizationId, String ruleId, String name, String ruleType, String pattern, String action, Boolean active) {
        GuardrailRuleEntity entity = guardrailRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown guardrail rule: " + ruleId));
        if (!entity.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Guardrail rule does not belong to organization: " + organizationId);
        }
        if (name != null && !name.isBlank()) {
            entity.setName(name);
        }
        if (ruleType != null && !ruleType.isBlank()) {
            entity.setRuleType(normalizeType(ruleType));
        }
        if (pattern != null && !pattern.isBlank()) {
            entity.setPattern(pattern);
        }
        if (action != null && !action.isBlank()) {
            entity.setAction(normalizeAction(action));
        }
        if (active != null) {
            entity.setActive(active);
        }
        GuardrailRuleEntity saved = guardrailRuleRepository.save(entity);
        auditLogService.record(organizationId, "guardrail_rule.update", "guardrail_rule", saved.getId(), Map.of(
                "name", saved.getName(),
                "rule_type", saved.getRuleType(),
                "pattern", saved.getPattern(),
                "action", saved.getAction(),
                "active", saved.isActive()
        ));
        return saved;
    }

    public void deleteRule(String organizationId, String ruleId) {
        GuardrailRuleEntity entity = guardrailRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown guardrail rule: " + ruleId));
        if (!entity.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Guardrail rule does not belong to organization: " + organizationId);
        }
        auditLogService.record(organizationId, "guardrail_rule.delete", "guardrail_rule", entity.getId(), Map.of(
                "name", entity.getName(),
                "rule_type", entity.getRuleType(),
                "pattern", entity.getPattern(),
                "action", entity.getAction(),
                "active", entity.isActive()
        ));
        guardrailRuleRepository.delete(entity);
    }

    public List<GuardrailViolationEntity> listViolations(String organizationId) {
        return guardrailViolationRepository.findByOrganizationId(organizationId, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Map<String, Object> stats(String organizationId) {
        List<GuardrailViolationEntity> violations = listViolations(organizationId);
        return Map.of(
                "violation_count", violations.size(),
                "rule_names", violations.stream().map(GuardrailViolationEntity::getRuleName).distinct().sorted().toList()
        );
    }

    public Map<String, Object> test(String organizationId, String text) {
        try {
            evaluate(organizationId, text, "/guardrails/test", null);
            return Map.of("blocked", false, "matched_rules", List.of());
        } catch (GuardrailViolationException ex) {
            List<String> matched = listRules(organizationId).stream()
                    .filter(GuardrailRuleEntity::isActive)
                    .filter(rule -> matches(rule, text))
                    .map(GuardrailRuleEntity::getName)
                    .toList();
            return Map.of("blocked", true, "matched_rules", matched, "message", ex.getMessage());
        }
    }

    public void evaluate(String organizationId, String text, String path, ApiKeyEntity apiKey) {
        if (organizationId == null || organizationId.isBlank() || text == null || text.isBlank()) {
            return;
        }
        for (GuardrailRuleEntity rule : listRules(organizationId)) {
            if (!rule.isActive()) {
                continue;
            }
            if (!matches(rule, text)) {
                continue;
            }
            guardrailViolationRepository.save(new GuardrailViolationEntity(
                    organizationId,
                    rule.getId(),
                    rule.getName(),
                    path,
                    rule.getAction(),
                    truncate(text),
                    apiKey == null ? null : apiKey.getId()
            ));
            if ("BLOCK".equals(rule.getAction())) {
                throw new GuardrailViolationException("Guardrail blocked request: " + rule.getName());
            }
        }
    }

    private boolean matches(GuardrailRuleEntity rule, String text) {
        if ("KEYWORD".equals(rule.getRuleType())) {
            return text.toLowerCase().contains(rule.getPattern().toLowerCase());
        }
        return false;
    }

    private String normalizeType(String value) {
        String normalized = value.toUpperCase();
        if (!List.of("KEYWORD").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported guardrail rule type: " + value);
        }
        return normalized;
    }

    private String normalizeAction(String value) {
        String normalized = value.toUpperCase();
        if (!List.of("BLOCK", "LOG").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported guardrail action: " + value);
        }
        return normalized;
    }

    private String truncate(String value) {
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
