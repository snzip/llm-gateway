package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.PasskeyEntity;
import com.qizlan.llm.gateway.persistence.entity.UserEntity;
import com.qizlan.llm.gateway.persistence.repository.PasskeyRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PasskeyService {

    private final PasskeyRepository passkeyRepository;

    public PasskeyService(PasskeyRepository passkeyRepository) {
        this.passkeyRepository = passkeyRepository;
    }

    public Optional<PasskeyEntity> findByIdAndUser(String id, UserEntity user) {
        return passkeyRepository.findById(id)
                .filter(passkey -> passkey.getUser().getId().equals(user.getId()));
    }

    public void delete(PasskeyEntity passkey) {
        passkeyRepository.delete(passkey);
    }
}
