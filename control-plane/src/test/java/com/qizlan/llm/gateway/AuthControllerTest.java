package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.gateway.api.AuthController;
import com.qizlan.llm.gateway.gateway.service.AuthService;
import com.qizlan.llm.gateway.gateway.service.SessionService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTest {

    @Test
    void loginReturnsToken() {
        AuthController authController = new AuthController(new StubAuthService());

        Map<String, Object> result = authController.login(new AuthController.AuthLoginRequest("admin@example.com", "password"));

        assertThat(result).containsEntry("access_token", "token");
        assertThat(result).containsEntry("token_type", "Bearer");
        assertThat(result).containsEntry("expires_in", 3600L);
    }

    @Test
    void logoutReturnsNoContentWhenAuthorized() {
        AuthController authController = new AuthController(new StubAuthService());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer session-token");

        var response = authController.logout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private static final class StubAuthService extends AuthService {
        private StubAuthService() {
            super(null, null, new SessionService(null, null));
        }

        @Override
        public Map<String, Object> login(String email, String password) {
            return Map.of("access_token", "token", "token_type", "Bearer", "expires_in", 3600L);
        }

        @Override
        public void logout(String token) {
        }
    }
}
