package com.qizlan.llm.gateway.gateway.service;

import com.qizlan.llm.gateway.persistence.entity.UserEntity;
import com.qizlan.llm.gateway.persistence.repository.UserRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserEntity create(String email, String name, String password) {
        UserEntity entity = new UserEntity();
        entity.setEmail(email);
        entity.setName(name);
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setOnboardingCompleted(false);
        entity.setActive(true);
        return userRepository.save(entity);
    }

    public Optional<UserEntity> findById(String id) {
        return userRepository.findById(id);
    }

    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public UserEntity updateProfile(UserEntity user, String name) {
        user.setName(name);
        return userRepository.save(user);
    }

    public UserEntity updatePassword(UserEntity user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    public UserEntity markOnboardingCompleted(UserEntity user) {
        user.setOnboardingCompleted(true);
        return userRepository.save(user);
    }

    public void delete(UserEntity user) {
        userRepository.delete(user);
    }
}
