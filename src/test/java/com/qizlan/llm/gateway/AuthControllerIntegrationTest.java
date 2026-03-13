package com.qizlan.llm.gateway;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

class AuthControllerIntegrationTest extends BaseGatewayTest {

    @Test
    void loginAndFetchProfile() {
        EntityExchangeResult<byte[]> loginResult = webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.remove("Authorization"))
                .bodyValue("{\"email\":\"admin@example.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();

        String token = read(loginResult, "/access_token");
        webTestClient.get().uri("/user/me")
                .headers(headers -> {
                    headers.remove("Authorization");
                    headers.setBearerAuth(token);
                })
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.email").isEqualTo("admin@example.com")
                .jsonPath("$.data.name").isNotEmpty();

        webTestClient.patch().uri("/user/me")
                .headers(headers -> {
                    headers.remove("Authorization");
                    headers.setBearerAuth(token);
                })
                .bodyValue(Map.of("name", "New Name"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.name").isEqualTo("New Name");

        webTestClient.post().uri("/user/me/complete-onboarding")
                .headers(headers -> {
                    headers.remove("Authorization");
                    headers.setBearerAuth(token);
                })
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.onboarding_completed").isEqualTo(true);

        webTestClient.put().uri("/user/password")
                .headers(headers -> {
                    headers.remove("Authorization");
                    headers.setBearerAuth(token);
                })
                .bodyValue(Map.of("newPassword", "new-password"))
                .exchange()
                .expectStatus().isNoContent();
    }
}
