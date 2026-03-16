package com.qizlan.llm.gateway.config;

import com.qizlan.llm.gateway.gateway.security.IamRuleService;
import com.qizlan.llm.gateway.gateway.service.ModelCatalogService;
import com.qizlan.llm.gateway.gateway.service.ModelRoutingCache;
import com.qizlan.llm.gateway.gateway.service.RoutingProjectionInvalidationService;
import com.qizlan.llm.gateway.gateway.service.RoutingProjectionStateService;
import com.qizlan.llm.gateway.gateway.service.ModelSyncService;
import com.qizlan.llm.gateway.gateway.service.ProviderAccessPolicy;
import com.qizlan.llm.gateway.gateway.service.ProviderAvailabilityPolicy;
import com.qizlan.llm.gateway.gateway.service.ProviderHealthService;
import com.qizlan.llm.gateway.gateway.service.ProviderSyncAdapterResolver;
import com.qizlan.llm.gateway.gateway.service.RoutingService;
import com.qizlan.llm.gateway.gateway.provider.MockProviderAdapter;
import com.qizlan.llm.gateway.gateway.provider.ProviderAdapter;
import com.qizlan.llm.gateway.persistence.repository.ModelRepository;
import com.qizlan.llm.gateway.persistence.repository.ModelSyncHistoryRepository;
import com.qizlan.llm.gateway.persistence.repository.ProviderRepository;
import com.qizlan.llm.gateway.persistence.entity.ApiKeyEntity;
import com.qizlan.llm.gateway.persistence.entity.ModelProviderMappingEntity;
import com.qizlan.llm.gateway.persistence.repository.ModelProviderMappingRepository;
import com.qizlan.llm.gateway.persistence.repository.ProjectionStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class GatewayRoutingConfiguration {

    @Bean
    public ModelRoutingCache modelRoutingCache() {
        return new ModelRoutingCache();
    }

    @Bean
    public ProviderAvailabilityPolicy providerAvailabilityPolicy(ProviderHealthService providerHealthService) {
        return providerHealthService::isAvailable;
    }

    @Bean
    public ProviderAccessPolicy providerAccessPolicy(IamRuleService iamRuleService) {
        return new ProviderAccessPolicy() {
            @Override
            public List<ModelProviderMappingEntity> filterAllowedProviders(ApiKeyEntity apiKey, List<ModelProviderMappingEntity> candidates) {
                return iamRuleService.filterAllowedProviders(apiKey, candidates);
            }
        };
    }

    @Bean
    public RoutingService routingService(
            ModelProviderMappingRepository mappingRepository,
            ProviderAvailabilityPolicy providerAvailabilityPolicy,
            ProviderAccessPolicy providerAccessPolicy,
            ModelRoutingCache modelRoutingCache,
            RoutingProjectionInvalidationService routingProjectionInvalidationService
    ) {
        return new RoutingService(mappingRepository, providerAvailabilityPolicy, providerAccessPolicy, modelRoutingCache, routingProjectionInvalidationService);
    }

    @Bean
    public RoutingProjectionStateService routingProjectionStateService(ProjectionStateRepository projectionStateRepository) {
        return new RoutingProjectionStateService(projectionStateRepository);
    }

    @Bean
    public RoutingProjectionInvalidationService routingProjectionInvalidationService(
            RoutingProjectionStateService routingProjectionStateService,
            ModelRoutingCache modelRoutingCache,
            GatewayProperties properties
    ) {
        return new RoutingProjectionInvalidationService(
                routingProjectionStateService,
                modelRoutingCache,
                properties.routing().projectionRefreshCheckMillis()
        );
    }

    @Bean
    public ModelCatalogService modelCatalogService(
            ModelProviderMappingRepository mappingRepository,
            RoutingProjectionInvalidationService routingProjectionInvalidationService
    ) {
        return new ModelCatalogService(mappingRepository, routingProjectionInvalidationService);
    }

    @Bean
    public ProviderSyncAdapterResolver providerSyncAdapterResolver(
            List<ProviderAdapter> providerAdapters,
            GatewayProperties properties,
            MockProviderAdapter mockProviderAdapter
    ) {
        return providerId -> {
            if ("mock".equalsIgnoreCase(properties.providers().mode())) {
                mockProviderAdapter.setCurrentProviderId(providerId);
                return mockProviderAdapter;
            }
            return providerAdapters.stream()
                    .filter(candidate -> candidate.providerId().equals(providerId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + providerId));
        };
    }

    @Bean
    public ModelSyncService modelSyncService(
            List<ProviderAdapter> providerAdapters,
            ProviderSyncAdapterResolver providerSyncAdapterResolver,
            ProviderRepository providerRepository,
            ModelRepository modelRepository,
            ModelProviderMappingRepository mappingRepository,
            ModelSyncHistoryRepository historyRepository,
            GatewayProperties properties,
            ObjectMapper objectMapper,
            RoutingService routingService,
            RoutingProjectionStateService routingProjectionStateService
    ) {
        return new ModelSyncService(
                providerAdapters,
                providerSyncAdapterResolver,
                providerRepository,
                modelRepository,
                mappingRepository,
                historyRepository,
                properties,
                objectMapper,
                routingService,
                routingProjectionStateService
        );
    }

    @Bean
    public ModelSyncScheduler modelSyncScheduler(ModelSyncService modelSyncService) {
        return new ModelSyncScheduler(modelSyncService);
    }

    public static final class ModelSyncScheduler {
        private final ModelSyncService modelSyncService;

        public ModelSyncScheduler(ModelSyncService modelSyncService) {
            this.modelSyncService = modelSyncService;
        }

        @Scheduled(fixedDelayString = "${llm.gateway.sync.fixed-delay-millis:3600000}")
        public void scheduledSync() {
            modelSyncService.scheduledSync();
        }
    }
}
