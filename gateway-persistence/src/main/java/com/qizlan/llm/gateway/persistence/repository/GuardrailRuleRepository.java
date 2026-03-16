package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.GuardrailRuleEntity;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardrailRuleRepository extends JpaRepository<GuardrailRuleEntity, String> {

    List<GuardrailRuleEntity> findByOrganizationId(String organizationId, Sort sort);
}
