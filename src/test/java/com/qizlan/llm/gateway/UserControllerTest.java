package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.TestEntityUtils;
import com.qizlan.llm.gateway.gateway.api.UserController;
import com.qizlan.llm.gateway.gateway.security.ApiKeyLookupCache;
import com.qizlan.llm.gateway.gateway.security.ApiKeyRateLimitService;
import com.qizlan.llm.gateway.gateway.security.ApiKeyTokenService;
import com.qizlan.llm.gateway.gateway.security.IamRuleService;
import com.qizlan.llm.gateway.gateway.service.GatewayMetricsService;
import com.qizlan.llm.gateway.gateway.service.PasskeyService;
import com.qizlan.llm.gateway.gateway.service.RequestContext;
import com.qizlan.llm.gateway.gateway.service.SessionService;
import com.qizlan.llm.gateway.gateway.service.UserService;
import com.qizlan.llm.gateway.persistence.entity.SessionEntity;
import com.qizlan.llm.gateway.persistence.entity.UserEntity;
import java.util.Map;
import java.util.Optional;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import io.micrometer.tracing.Tracer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(UserController.class)
@AutoConfigureWebTestClient
@ImportAutoConfiguration(exclude = {
        ReactiveSecurityAutoConfiguration.class,
        ReactiveUserDetailsServiceAutoConfiguration.class
})
@Import(TestGatewayConfig.class)
class UserControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private UserService userService;

    @MockBean
    private PasskeyService passkeyService;

    @MockBean
    private com.qizlan.llm.gateway.gateway.service.RequestContextService requestContextService;

    @MockBean
    private GatewayMetricsService gatewayMetricsService;

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

    private UserEntity currentUser;

    @BeforeEach
    void setUp() {
        RequestContext requestContext = new RequestContext("user-corr", "user-trace", "user-span", "system", "control-plane");
        when(requestContextService.create(any(), any(), any())).thenReturn(requestContext);
        when(requestContextService.get(any(ServerWebExchange.class))).thenReturn(requestContext);
        currentUser = new UserEntity();
        currentUser.setName("Alice");
        currentUser.setEmail("alice@example.com");
        TestEntityUtils.setId(currentUser, "user-1");
        currentUser.setOnboardingCompleted(false);
        SessionEntity session = new SessionEntity();
        session.setUser(currentUser);
        when(sessionService.validate("test-token")).thenReturn(Optional.of(session));
        when(sessionService.currentUser(any())).thenReturn(Optional.of(currentUser));
        Mockito.doNothing().when(sessionService).bind(any(), any());
    }

    @Test
    void meReturnsUserProfile() {
        webTestClient.get().uri("/user/me")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.email").isEqualTo("alice@example.com");
    }

    @Test
    void updateProfileAcceptsNewName() {
        when(userService.updateProfile(any(), anyString())).thenReturn(currentUser);
        webTestClient.patch().uri("/user/me")
                .header("Authorization", "Bearer test-token")
                .bodyValue(Map.of("name", "Bob"))
                .exchange()
                .expectStatus().isOk();
    }
}
