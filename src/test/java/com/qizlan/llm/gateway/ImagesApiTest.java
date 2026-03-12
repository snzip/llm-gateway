package com.qizlan.llm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagesApiTest extends AbstractIntegrationTest {

    @Test
    void imageEndpointsAndResponsesPlaceholderWork() {
        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"inlineData": {"data": "R0lGODlhAQABAIAAAAUEBA=="}}
                      ]
                    }
                  }]
                }
                """));

        webTestClient.post().uri("/v1/images/generations")
                .header("Authorization", "Bearer test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"prompt":"draw a cat","model":"gemini-2.5-flash-image"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].b64_json").isEqualTo("R0lGODlhAQABAIAAAAUEBA==");

        GOOGLE_RESPONSES.add(json("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"inlineData": {"data": "Q0FUVEVTVA=="}}]
                    }
                  }]
                }
                """));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("prompt", "edit this");
        builder.part("image", new NamedByteArrayResource("test.png", "png".getBytes()))
                .contentType(MediaType.IMAGE_PNG);

        webTestClient.post().uri("/v1/images/edits")
                .header("Authorization", "Bearer test-api-key")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].revised_prompt").isEqualTo("edit this");

        webTestClient.post().uri("/v1/responses")
                .header("Authorization", "Bearer test-api-key")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class).value(body -> assertTrue(body.contains("not supported")));

        webTestClient.post().uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-4o","messages":[{"role":"user","content":"hello gateway"}]}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
