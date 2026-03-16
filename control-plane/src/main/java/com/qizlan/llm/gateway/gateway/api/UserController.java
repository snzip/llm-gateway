package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.service.PasskeyService;
import com.qizlan.llm.gateway.gateway.service.SessionService;
import com.qizlan.llm.gateway.gateway.service.UserService;
import com.qizlan.llm.gateway.persistence.entity.PasskeyEntity;
import com.qizlan.llm.gateway.persistence.entity.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping
@io.swagger.v3.oas.annotations.tags.Tag(name = "User", description = "当前用户资料与 passkey 管理")
public class UserController {

    private final SessionService sessionService;
    private final UserService userService;
    private final PasskeyService passkeyService;

    public UserController(SessionService sessionService, UserService userService, PasskeyService passkeyService) {
        this.sessionService = sessionService;
        this.userService = userService;
        this.passkeyService = passkeyService;
    }

    @GetMapping("/user/me")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get current user", description = "Returns lightweight profile metadata for the authenticated user.")
    public Map<String, Object> me(HttpServletRequest request) {
        return Map.of("data", mapUser(currentUser(request)));
    }

    @PatchMapping("/user/me")
    @io.swagger.v3.oas.annotations.Operation(summary = "Update current profile", description = "Update the name field for the currently logged in user.")
    public Map<String, Object> updateProfile(@Valid @RequestBody UserProfileUpdateRequest body, HttpServletRequest request) {
        UserEntity user = currentUser(request);
        return Map.of("data", mapUser(userService.updateProfile(user, body.name())));
    }

    @PutMapping("/user/password")
    @io.swagger.v3.oas.annotations.Operation(summary = "Change password", description = "Replace the password for the authenticated control-plane user.")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody UserPasswordUpdateRequest body, HttpServletRequest request) {
        UserEntity user = currentUser(request);
        userService.updatePassword(user, body.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/user/me")
    @io.swagger.v3.oas.annotations.Operation(summary = "Delete user account", description = "Delete the authenticated user record.")
    public ResponseEntity<Void> deleteAccount(HttpServletRequest request) {
        userService.delete(currentUser(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/user/me/complete-onboarding")
    @io.swagger.v3.oas.annotations.Operation(summary = "Mark onboarding complete", description = "Flags the onboarding flag on the current user.")
    public Map<String, Object> completeOnboarding(HttpServletRequest request) {
        return Map.of("data", mapUser(userService.markOnboardingCompleted(currentUser(request))));
    }

    @DeleteMapping("/user/me/passkeys/{id}")
    @io.swagger.v3.oas.annotations.Operation(summary = "Revoke passkey", description = "Deletes a registered passkey for the current user.")
    public ResponseEntity<Void> deletePasskey(@PathVariable("id") String id, HttpServletRequest request) {
        UserEntity user = currentUser(request);
        PasskeyEntity passkey = passkeyService.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passkey not found"));
        passkeyService.delete(passkey);
        return ResponseEntity.noContent().build();
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return sessionService.currentUser(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session missing"));
    }

    private Map<String, Object> mapUser(UserEntity user) {
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "name", user.getName(),
                "onboarding_completed", user.isOnboardingCompleted()
        );
    }

    public record UserProfileUpdateRequest(@NotBlank String name) {
    }

    public record UserPasswordUpdateRequest(@NotBlank String newPassword) {
    }
}
