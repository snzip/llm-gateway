package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

class McpControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void oauthAndMcpEndpointsWorkWithBearerProtection() {
        EntityExchangeResult<byte[]> registerResult = webTestClient.post().uri("/oauth/register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"client_name":"Gateway MCP Client","redirect_uri":"https://client.example/callback"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        String clientId = read(registerResult, "/client_id");
        String clientSecret = read(registerResult, "/client_secret");

        EntityExchangeResult<byte[]> authorizeResult = webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/oauth/authorize")
                        .queryParam("client_id", clientId)
                        .queryParam("redirect_uri", "https://client.example/callback")
                        .queryParam("state", "abc")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        String code = read(authorizeResult, "/code");

        EntityExchangeResult<byte[]> tokenResult = webTestClient.post().uri("/oauth/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"grant_type":"authorization_code","code":"%s","scope":"mcp tools"}
                        """.formatted(code))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        String accessToken = read(tokenResult, "/access_token");
        String refreshToken = read(tokenResult, "/refresh_token");

        webTestClient.get().uri("/.well-known/oauth-authorization-server")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.issuer").exists();

        webTestClient.post().uri("/mcp")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"method":"initialize","client":{"name":"test-client"},"protocol_version":"2025-03-26"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.session_id").exists();

        webTestClient.post().uri("/mcp")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"method":"tools/list"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tools").isArray();

        OPENAI_RESPONSES.add(json("""
                {
                  "id": "chatcmpl-mcp",
                  "choices": [{"message": {"role": "assistant", "content": "MCP says hello"}}],
                  "usage": {"prompt_tokens": 9, "completion_tokens": 4, "total_tokens": 13}
                }
                """));

        webTestClient.post().uri("/mcp")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"method":"tools/call","name":"chat","arguments":{"model":"gpt-4o","prompt":"hello from mcp"}}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post().uri("/mcp")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"method":"tools/call","name":"chat","arguments":{"model":"gpt-4o","prompt":"hello from mcp"}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result.choices[0].message.content").isEqualTo("MCP says hello");

        EntityExchangeResult<byte[]> refreshResult = webTestClient.post().uri("/oauth/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"grant_type":"refresh_token","client_id":"%s","client_secret":"%s","refresh_token":"%s","scope":"mcp tools"}
                        """.formatted(clientId, clientSecret, refreshToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();
        String refreshedAccessToken = read(refreshResult, "/access_token");

        webTestClient.post().uri("/oauth/revoke")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"token":"%s"}
                        """.formatted(refreshedAccessToken))
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.post().uri("/oauth/clients/" + clientId + "/rotate-secret")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.client_id").isEqualTo(clientId)
                .jsonPath("$.client_secret").exists();
    }
}
