package com.qizlan.llm.gateway;

import io.swagger.v3.oas.models.OpenAPI;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springdoc.core.service.OpenAPIService;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerIntegrationTest extends BaseGatewayTest {

    @Autowired
    private OpenAPIService openAPIService;

    @Test
    void openApiDocumentExposesBearerSecurityAndTags() {
        OpenAPI openAPI = openAPIService.build(Locale.ENGLISH);
        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getComponents()).isNotNull();
        var securitySchemes = openAPI.getComponents().getSecuritySchemes();
        assertThat(securitySchemes).containsKey("BearerAuth");
        var bearer = securitySchemes.get("BearerAuth");
        assertThat(bearer.getType().toString().toLowerCase()).isEqualTo("http");
        assertThat(bearer.getScheme()).isEqualTo("bearer");

        var tags = openAPI.getTags();
        assertThat(tags).extracting(io.swagger.v3.oas.models.tags.Tag::getName)
                .contains("Data Plane", "Control Plane", "MCP");
    }
}
