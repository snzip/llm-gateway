package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.gateway.api.UserController;
import com.qizlan.llm.gateway.gateway.service.PasskeyService;
import com.qizlan.llm.gateway.gateway.service.SessionService;
import com.qizlan.llm.gateway.gateway.service.UserService;
import com.qizlan.llm.gateway.persistence.entity.UserEntity;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerTest {

    private UserEntity currentUser;
    private MockHttpServletRequest request;
    private UserController userController;

    @BeforeEach
    void setUp() {
        currentUser = new UserEntity();
        ReflectionTestUtils.setField(currentUser, "id", "user-1");
        currentUser.setName("Alice");
        currentUser.setEmail("alice@example.com");
        currentUser.setOnboardingCompleted(false);

        request = new MockHttpServletRequest();
        userController = new UserController(
                new StubSessionService(currentUser),
                new StubUserService(currentUser),
                new StubPasskeyService()
        );
    }

    @Test
    void meReturnsUserProfile() {
        Map<String, Object> result = userController.me(request);

        assertThat(result).containsKey("data");
        assertThat(((Map<?, ?>) result.get("data")).get("email")).isEqualTo("alice@example.com");
    }

    @Test
    void updateProfileAcceptsNewName() {
        Map<String, Object> result = userController.updateProfile(new UserController.UserProfileUpdateRequest("Bob"), request);

        assertThat(result).containsKey("data");
        assertThat(((Map<?, ?>) result.get("data")).get("email")).isEqualTo("alice@example.com");
    }

    private static final class StubSessionService extends SessionService {
        private final UserEntity user;

        private StubSessionService(UserEntity user) {
            super(null, null);
            this.user = user;
        }

        @Override
        public Optional<UserEntity> currentUser(jakarta.servlet.http.HttpServletRequest request) {
            return Optional.of(user);
        }
    }

    private static final class StubUserService extends UserService {
        private final UserEntity user;

        private StubUserService(UserEntity user) {
            super(null, null);
            this.user = user;
        }

        @Override
        public UserEntity updateProfile(UserEntity user, String name) {
            this.user.setName(name);
            return this.user;
        }
    }

    private static final class StubPasskeyService extends PasskeyService {
        private StubPasskeyService() {
            super(null);
        }
    }
}
