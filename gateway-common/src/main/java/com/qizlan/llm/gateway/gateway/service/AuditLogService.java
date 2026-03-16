package com.qizlan.llm.gateway.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.persistence.entity.AuditLogEntity;
import com.qizlan.llm.gateway.persistence.repository.AuditLogRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(RequestContext context, String organizationId, String action, String resourceType, String resourceId, Map<String, Object> detail) {
        record(context, organizationId, action, resourceType, resourceId, null, null, detail);
    }

    public void record(
            RequestContext context,
            String organizationId,
            String action,
            String resourceType,
            String resourceId,
            String parentResourceType,
            String parentResourceId,
            Map<String, Object> detail
    ) {
        if (organizationId == null || organizationId.isBlank()) {
            return;
        }
        AuditLogEntity entity = new AuditLogEntity(
                organizationId,
                context.actorType(),
                context.actorId(),
                action,
                resourceType,
                resourceId,
                parentResourceType,
                parentResourceId,
                serialize(detail),
                context.correlationId()
        );
        auditLogRepository.save(entity);
    }

    public List<AuditLogEntity> list(String organizationId) {
        return auditLogRepository.findByOrganizationId(organizationId, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Map<String, Object> filters(String organizationId) {
        List<AuditLogEntity> logs = list(organizationId);
        return Map.of(
                "actions", logs.stream().map(AuditLogEntity::getAction).distinct().sorted().toList(),
                "resource_types", logs.stream().map(AuditLogEntity::getResourceType).distinct().sorted().toList(),
                "actor_types", logs.stream().map(AuditLogEntity::getActorType).distinct().sorted().toList()
        );
    }

    private String serialize(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail == null ? Map.of() : detail);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
