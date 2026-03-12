package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "model")
public class ModelEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String family;

    @Column(nullable = false)
    private boolean freeModel;

    @Column(nullable = false)
    private boolean supportsVision;

    @Column(nullable = false)
    private boolean supportsTools;

    @Column(nullable = false)
    private boolean supportsReasoning;

    @Column(nullable = false)
    private boolean supportsStreaming;

    @Column(nullable = false)
    private boolean imageGeneration;

    @Column(nullable = false)
    private int contextWindowTokens;

    @Column(nullable = false)
    private long inputCostMicrosPerToken;

    @Column(nullable = false)
    private long outputCostMicrosPerToken;

    @Column(nullable = false)
    private boolean archived;

    protected ModelEntity() {
    }

    public static ModelEntity textModel(
            String id,
            String name,
            String family,
            boolean freeModel,
            boolean supportsTools,
            boolean supportsReasoning,
            boolean supportsStreaming,
            boolean supportsVision
    ) {
        return new ModelEntity(id, name, family, freeModel, supportsVision, supportsTools, supportsReasoning, supportsStreaming, false);
    }

    public static ModelEntity imageModel(String id, String name, String family) {
        return new ModelEntity(id, name, family, false, false, false, true, false, true);
    }

    public ModelEntity(
            String id,
            String name,
            String family,
            boolean freeModel,
            boolean supportsVision,
            boolean supportsTools,
            boolean supportsReasoning,
            boolean supportsStreaming,
            boolean imageGeneration
    ) {
        this.id = id;
        this.name = name;
        this.family = family;
        this.freeModel = freeModel;
        this.supportsVision = supportsVision;
        this.supportsTools = supportsTools;
        this.supportsReasoning = supportsReasoning;
        this.supportsStreaming = supportsStreaming;
        this.imageGeneration = imageGeneration;
        this.contextWindowTokens = 0;
        this.inputCostMicrosPerToken = 0L;
        this.outputCostMicrosPerToken = 0L;
        this.archived = false;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFamily() {
        return family;
    }

    public boolean isFreeModel() {
        return freeModel;
    }

    public boolean isSupportsVision() {
        return supportsVision;
    }

    public boolean isSupportsTools() {
        return supportsTools;
    }

    public boolean isSupportsReasoning() {
        return supportsReasoning;
    }

    public boolean isSupportsStreaming() {
        return supportsStreaming;
    }

    public boolean isImageGeneration() {
        return imageGeneration;
    }

    public int getContextWindowTokens() {
        return contextWindowTokens;
    }

    public long getInputCostMicrosPerToken() {
        return inputCostMicrosPerToken;
    }

    public long getOutputCostMicrosPerToken() {
        return outputCostMicrosPerToken;
    }

    public boolean isArchived() {
        return archived;
    }

    public void refreshMetadata(
            String name,
            String family,
            boolean freeModel,
            boolean supportsVision,
            boolean supportsTools,
            boolean supportsReasoning,
            boolean supportsStreaming,
            boolean imageGeneration,
            int contextWindowTokens,
            long inputCostMicrosPerToken,
            long outputCostMicrosPerToken
    ) {
        this.name = name;
        this.family = family;
        this.freeModel = freeModel;
        this.supportsVision = supportsVision;
        this.supportsTools = supportsTools;
        this.supportsReasoning = supportsReasoning;
        this.supportsStreaming = supportsStreaming;
        this.imageGeneration = imageGeneration;
        this.contextWindowTokens = contextWindowTokens;
        this.inputCostMicrosPerToken = inputCostMicrosPerToken;
        this.outputCostMicrosPerToken = outputCostMicrosPerToken;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}
