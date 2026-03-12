package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "model_provider_mapping")
public class ModelProviderMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id")
    private ModelEntity model;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id")
    private ProviderEntity provider;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private boolean streaming;

    @Column(nullable = false)
    private boolean vision;

    @Column(nullable = false)
    private boolean tools;

    @Column(nullable = false)
    private boolean reasoning;

    @Column(nullable = false)
    private boolean imageGeneration;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean syncManaged;

    protected ModelProviderMappingEntity() {
    }

    public static ModelProviderMappingEntity of(
            ModelEntity model,
            ProviderEntity provider,
            String modelName,
            boolean streaming,
            boolean vision,
            boolean tools,
            boolean reasoning,
            boolean imageGeneration,
            int priority
    ) {
        ModelProviderMappingEntity entity = new ModelProviderMappingEntity();
        entity.model = model;
        entity.provider = provider;
        entity.modelName = modelName;
        entity.streaming = streaming;
        entity.vision = vision;
        entity.tools = tools;
        entity.reasoning = reasoning;
        entity.imageGeneration = imageGeneration;
        entity.priority = priority;
        entity.active = true;
        entity.syncManaged = false;
        return entity;
    }

    public ModelEntity getModel() {
        return model;
    }

    public ProviderEntity getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public boolean isVision() {
        return vision;
    }

    public boolean isTools() {
        return tools;
    }

    public boolean isReasoning() {
        return reasoning;
    }

    public boolean isImageGeneration() {
        return imageGeneration;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }

    public void refresh(
            String modelName,
            boolean streaming,
            boolean vision,
            boolean tools,
            boolean reasoning,
            boolean imageGeneration,
            int priority,
            boolean active,
            boolean syncManaged
    ) {
        this.modelName = modelName;
        this.streaming = streaming;
        this.vision = vision;
        this.tools = tools;
        this.reasoning = reasoning;
        this.imageGeneration = imageGeneration;
        this.priority = priority;
        this.active = active;
        this.syncManaged = syncManaged;
    }

    public boolean isSyncManaged() {
        return syncManaged;
    }
}
