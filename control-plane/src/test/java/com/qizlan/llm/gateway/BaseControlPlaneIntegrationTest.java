package com.qizlan.llm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizlan.llm.gateway.controlplane.ControlPlaneApplication;
import com.qizlan.llm.gateway.persistence.repository.RequestLogRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ControlPlaneApplication.class)
@AutoConfigureMockMvc
abstract class BaseControlPlaneIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RequestLogRepository requestLogRepository;

    protected String read(MvcResult result, String pointer) {
        try {
            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            JsonNode node = root.at(pointer);
            return node.isMissingNode() || node.isNull() ? "" : node.asText();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected String uniqueName(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    protected String createOrganization(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, "/id");
    }

    protected String createProject(String organizationId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "organizationId", organizationId,
                                "name", name))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, "/id");
    }

    protected MvcResult createApiKey(String organizationId, String projectId, String name) throws Exception {
        return mockMvc.perform(post("/keys/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "organizationId", organizationId,
                                "projectId", projectId,
                                "name", name))))
                .andExpect(status().isOk())
                .andReturn();
    }

    protected String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, "/access_token");
    }
}
