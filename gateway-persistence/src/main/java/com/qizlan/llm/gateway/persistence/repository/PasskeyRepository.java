package com.qizlan.llm.gateway.persistence.repository;

import com.qizlan.llm.gateway.persistence.entity.PasskeyEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasskeyRepository extends JpaRepository<PasskeyEntity, String> {

    Optional<PasskeyEntity> findByCredentialId(String credentialId);
}
