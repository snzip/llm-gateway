package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.TestEntityUtils;
import com.qizlan.llm.gateway.gateway.api.AuthController;
import com.qizlan.llm.gateway.gateway.security.ApiKeyLookupCache;
import com.qizlan.llm.gateway.gateway.security.ApiKeyRateLimitService;
import com.qizlan.llm.gateway.gateway.security.ApiKeyTokenService;
import com.qizlan.llm.gateway.gateway.security.IamRuleService;
import com.qizlan.llm.gateway.gateway.api.AuthController;
import com.qizlan.llm.gateway.gateway.security.ApiKeyLookupCache;
import com.qizlan.llm.gateway.gateway.security.ApiKeyRateLimitService;
import com.qizlan.llm.gateway.gateway.security.ApiKeyTokenService;
import com.qizlan.llm.gateway.gateway.security.IamRuleService;
import com.qizlan.llm.gateway.gateway.service.AuthService;
import com.qizlan.llm.gateway.gateway.service.GatewayMetricsService;
import com.qizlan.llm.gateway.gateway.service.RequestContext;
import com.qizlan.llm.gateway.gateway.service.SessionService;
import com.qizlan.llm.gateway.persistence.entity.SessionEntity;
import com.qizlan.llm.gateway.persistence.entity.UserEntity;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import io.micrometer.tracing.Tracer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@WebFluxTest(AuthController.class)
@AutoConfigureWebTestClient
@ImportAutoConfiguration(exclude = {
        ReactiveSecurityAutoConfiguration.class,
        ReactiveUserDetailsServiceAutoConfiguration.class
})
@Import(TestGatewayConfig.class)
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthService authService;

    @MockBean
    private com.qizlan.llm.gateway.gateway.service.RequestContextService requestContextService;

    @MockBean
    private GatewayMetricsService gatewayMetricsService;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private Tracer tracer;

    @MockBean
    private ApiKeyTokenService apiKeyTokenService;

    @MockBean
    private IamRuleService iamRuleService;

    @MockBean
    private ApiKeyRateLimitService apiKeyRateLimitService;

    @MockBean
    private ApiKeyLookupCache apiKeyLookupCache;

    @BeforeEach
    void initRequestContext() {
        RequestContext requestContext = new RequestContext("auth-corr", "auth-trace", "auth-span", "system", "control-plane");
        when(requestContextService.create(any(), any(), any())).thenReturn(requestContext);
        when(requestContextService.get(any(ServerWebExchange.class))).thenReturn(requestContext);

        SessionEntity session = new SessionEntity();
        UserEntity user = new UserEntity();
        TestEntityUtils.setId(user, "admin-1");
        user.setName("Admin");
        user.setEmail("admin@example.com");
        session.setUser(user);
        when(sessionService.validate("session-token")).thenReturn(Optional.of(session));
        Mockito.doNothing().when(sessionService).bind(any(ServerWebExchange.class), any(SessionEntity.class));
    }

    @Test
    void loginReturnsToken() {
        when(authService.login(anyString(), anyString()))
                .thenReturn(Map.of("access_token", "token", "token_type", "Bearer", "expires_in", 3600));

        webTestClient.post().uri("/auth/login")
                .bodyValue(Map.of("email", "admin@example.com", "password", "password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.access_token").isEqualTo("token")
                .jsonPath("$.token_type").isEqualTo("Bearer")
                .jsonPath("$.expires_in").isEqualTo(3600);
    }

    @Test
    void logoutReturnsNoContentWhenAuthorized() {
        doNothing().when(authService).logout("session-token");

        webTestClient.post().uri("/auth/logout")
                .header("Authorization", "Bearer session-token")
                .exchange()
                .expectStatus().isNoContent();
    }
}
