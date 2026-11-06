package com.jobpilot.offer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** Requisito extraído de una oferta (tabla {@code offer_requirements}). */
@Entity
@Table(name = "offer_requirements")
public class OfferRequirement {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id")
    private JobOffer offer;

    private String description;

    @Enumerated(EnumType.STRING)
    private RequirementType type;

    @Enumerated(EnumType.STRING)
    private RequirementCategory category;

    private short sortOrder;

    protected OfferRequirement() {
    }

    public OfferRequirement(JobOffer offer, String description, RequirementType type, RequirementCategory category,
            short sortOrder) {
        this.id = UUID.randomUUID();
        this.offer = offer;
        this.description = description;
        this.type = type;
        this.category = category;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public JobOffer getOffer() {
        return offer;
    }

    public String getDescription() {
        return description;
    }

    public RequirementType getType() {
        return type;
    }

    public RequirementCategory getCategory() {
        return category;
    }

    public short getSortOrder() {
        return sortOrder;
    }
}
