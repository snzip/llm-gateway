package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.persistence.entity.OrganizationEntity;
import com.qizlan.llm.gateway.persistence.entity.ProjectEntity;
import com.qizlan.llm.gateway.persistence.repository.OrganizationRepository;
import com.qizlan.llm.gateway.persistence.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JpaRelationshipMappingTest extends BaseGatewayTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    @Transactional
    void organizationAndProjectExposeRelatedCollections() {
        String organizationId = createOrganization(uniqueName("Jpa Org"));
        String projectId = createProject(organizationId, uniqueName("Jpa Project"));
        EntityExchangeResult<byte[]> keyResult = createApiKey(organizationId, projectId, uniqueName("Jpa Key"));
        String apiKeyId = read(keyResult, "/id");

        OrganizationEntity organization = organizationRepository.findById(organizationId).orElseThrow();
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow();

        assertEquals(1, organization.getProjects().size());
        assertEquals(projectId, organization.getProjects().iterator().next().getId());
        assertEquals(1, organization.getApiKeys().size());
        assertEquals(apiKeyId, organization.getApiKeys().iterator().next().getId());
        assertEquals(1, project.getApiKeys().size());
        assertEquals(apiKeyId, project.getApiKeys().iterator().next().getId());
    }
}
