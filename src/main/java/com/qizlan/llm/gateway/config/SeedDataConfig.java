package com.qizlan.llm.gateway.config;

import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import com.qizlan.llm.gateway.persistence.entity.OrganizationEntity;
import com.qizlan.llm.gateway.persistence.entity.ProjectEntity;
import com.qizlan.llm.gateway.persistence.entity.ProviderEntity;
import com.qizlan.llm.gateway.persistence.repository.ApiKeyRepository;
import com.qizlan.llm.gateway.persistence.repository.ModelProviderMappingRepository;
import com.qizlan.llm.gateway.persistence.repository.ModelRepository;
import com.qizlan.llm.gateway.persistence.repository.OrganizationRepository;
import com.qizlan.llm.gateway.persistence.repository.ProjectRepository;
import com.qizlan.llm.gateway.persistence.repository.ProviderRepository;
import com.qizlan.llm.gateway.gateway.security.ApiKeyTokenService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedDataConfig {

    @Bean
    CommandLineRunner seedData(
            GatewayProperties properties,
            ApiKeyRepository apiKeyRepository,
            ApiKeyTokenService apiKeyTokenService,
            OrganizationRepository organizationRepository,
            ProjectRepository projectRepository,
            ProviderRepository providerRepository,
            ModelRepository modelRepository,
            ModelProviderMappingRepository mappingRepository
    ) {
        return args -> {
            OrganizationEntity defaultOrg = organizationRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(() -> organizationRepository.save(new OrganizationEntity("Default Organization")));
            ProjectEntity defaultProject = projectRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(() -> projectRepository.save(new ProjectEntity("Default Project", defaultOrg)));
            if (apiKeyRepository.count() == 0 && properties.seed() != null && properties.seed().enabled()) {
                String seedToken = properties.seed().apiKey();
                if (seedToken == null || seedToken.isBlank()) {
                    throw new IllegalStateException("Seed API key is enabled but llm.gateway.seed.api-key is blank");
                }
                apiKeyRepository.save(new ApiKeyEntity(
                        apiKeyTokenService.hash(seedToken),
                        apiKeyTokenService.prefix(seedToken),
                        properties.seed().name() == null || properties.seed().name().isBlank() ? "Seed key" : properties.seed().name(),
                        true,
                        defaultOrg,
                        defaultProject
                ));
            }

            ProviderEntity openai = providerRepository.findById("openai")
                    .orElseGet(() -> providerRepository.save(new ProviderEntity("openai", "OpenAI", true, true)));
            ProviderEntity anthropic = providerRepository.findById("anthropic")
                    .orElseGet(() -> providerRepository.save(new ProviderEntity("anthropic", "Anthropic", true, false)));
            ProviderEntity google = providerRepository.findById("google")
                    .orElseGet(() -> providerRepository.save(new ProviderEntity("google", "Google", true, true)));

            ModelEntity gpt4o = modelRepository.findById("gpt-4o")
                    .orElseGet(() -> modelRepository.save(ModelEntity.textModel("gpt-4o", "GPT-4o", "openai", false, true, true, true, true)));
            ModelEntity claude = modelRepository.findById("claude-3-5-sonnet")
                    .orElseGet(() -> modelRepository.save(ModelEntity.textModel("claude-3-5-sonnet", "Claude 3.5 Sonnet", "anthropic", false, true, true, true, false)));
            ModelEntity geminiText = modelRepository.findById("gemini-2.0-flash")
                    .orElseGet(() -> modelRepository.save(ModelEntity.textModel("gemini-2.0-flash", "Gemini 2.0 Flash", "google", false, false, false, true, false)));
            ModelEntity geminiImage = modelRepository.findById("gemini-2.5-flash-image")
                    .orElseGet(() -> modelRepository.save(ModelEntity.imageModel("gemini-2.5-flash-image", "Gemini 2.5 Flash Image", "google")));

            if (!mappingRepository.existsByModelIdAndProviderId("gpt-4o", "openai")) {
                mappingRepository.save(ModelProviderMappingEntity.of(gpt4o, openai, "gpt-4o", true, false, true, true, true, 10));
            }
            if (!mappingRepository.existsByModelIdAndProviderId("claude-3-5-sonnet", "anthropic")) {
                mappingRepository.save(ModelProviderMappingEntity.of(claude, anthropic, "claude-3-5-sonnet", true, false, true, true, false, 10));
            }
            if (!mappingRepository.existsByModelIdAndProviderId("gemini-2.0-flash", "google")) {
                mappingRepository.save(ModelProviderMappingEntity.of(geminiText, google, "gemini-2.0-flash", true, false, false, false, false, 10));
            }
            if (!mappingRepository.existsByModelIdAndProviderId("gemini-2.5-flash-image", "google")) {
                mappingRepository.save(ModelProviderMappingEntity.of(geminiImage, google, "gemini-2.5-flash-image", true, true, false, false, false, 10));
            }

            ModelEntity gatewayText = modelRepository.findById("gateway-text")
                    .orElseGet(() -> modelRepository.save(ModelEntity.textModel("gateway-text", "Gateway Text", "gateway", false, true, true, true, false)));
            if (!mappingRepository.existsByModelIdAndProviderId("gateway-text", "openai")) {
                mappingRepository.save(ModelProviderMappingEntity.of(gatewayText, openai, "gpt-4o", true, false, true, true, false, 10));
            }
            if (!mappingRepository.existsByModelIdAndProviderId("gateway-text", "anthropic")) {
                mappingRepository.save(ModelProviderMappingEntity.of(gatewayText, anthropic, "claude-3-5-sonnet", true, false, true, true, false, 20));
            }
            if (!mappingRepository.existsByModelIdAndProviderId("gateway-text", "google")) {
                mappingRepository.save(ModelProviderMappingEntity.of(gatewayText, google, "gemini-2.0-flash", true, false, false, false, false, 30));
            }
        };
    }
}
