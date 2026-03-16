package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.GuardrailViolationEntity;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardrailViolationRepository extends JpaRepository<GuardrailViolationEntity, String> {

    List<GuardrailViolationEntity> findByOrganizationId(String organizationId, Sort sort);
}
