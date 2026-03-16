package com.qizlan.llm.gateway;

import com.qizlan.llm.gateway.persistence.entity.OrganizationEntity;
import com.qizlan.llm.gateway.persistence.entity.ProjectEntity;
import com.qizlan.llm.gateway.persistence.repository.OrganizationRepository;
import com.qizlan.llm.gateway.persistence.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JpaRelationshipMappingTest extends BaseControlPlaneIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void organizationAndProjectExposeRelatedCollections() throws Exception {
        String organizationId = createOrganization(uniqueName("Jpa Org"));
        String projectId = createProject(organizationId, uniqueName("Jpa Project"));
        String apiKeyId = read(createApiKey(organizationId, projectId, uniqueName("Jpa Key")), "/id");

        entityManager.flush();
        entityManager.clear();

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
