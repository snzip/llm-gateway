package com.qizlan.llm.gateway;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends BaseControlPlaneIntegrationTest {

    @Test
    void loginAndFetchProfile() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = read(loginResult, "/access_token");

        mockMvc.perform(get("/user/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.name").isNotEmpty());

        mockMvc.perform(patch("/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New Name"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"));

        mockMvc.perform(post("/user/me/complete-onboarding")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarding_completed").value(true));

        mockMvc.perform(put("/user/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "new-password"))))
                .andExpect(status().isNoContent());
    }
}
