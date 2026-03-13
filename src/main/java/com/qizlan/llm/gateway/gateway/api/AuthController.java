package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Auth", description = "公共认证与个人资料管理")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.Operation(summary = "Authenticate a control-plane user", description = "Exchange email/password for a session token. Returns a Bearer token used by control-plane endpoints.")
    public Mono<Map<String, Object>> login(@Valid @RequestBody AuthLoginRequest request) {
        return Mono.fromCallable(() -> authService.login(request.email(), request.password()));
    }

    @PostMapping("/logout")
    @io.swagger.v3.oas.annotations.Operation(summary = "Revoke current control-plane session", description = "Invalidate the current session token so future calls require re-login.")
    public Mono<ResponseEntity<Void>> logout(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            String header = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String token = header.substring("Bearer ".length()).trim();
            authService.logout(token);
            return ResponseEntity.noContent().build();
        });
    }

    public record AuthLoginRequest(@NotBlank String email, @NotBlank String password) {
    }
}
