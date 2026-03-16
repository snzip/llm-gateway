package com.qizlan.llm.gateway.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "provider")
public class ProviderEntity {

    @Id
    private String id;

    private String name;

    private boolean streaming;

    private boolean cancellation;

    protected ProviderEntity() {
    }

    public ProviderEntity(String id, String name, boolean streaming, boolean cancellation) {
        this.id = id;
        this.name = name;
        this.streaming = streaming;
        this.cancellation = cancellation;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
