package com.jobpilot.profile.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Experiencia laboral del perfil (tabla {@code experiences}). {@code endDate} null = puesto actual. */
@Entity
@Table(name = "experiences")
public class Experience {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    private String company;

    private String role;

    private LocalDate startDate;

    private LocalDate endDate;

    private String description;

    private short sortOrder;

    protected Experience() {
    }

    public Experience(Profile profile, String company, String role, LocalDate startDate, LocalDate endDate,
            String description, short sortOrder) {
        this.id = UUID.randomUUID();
        this.profile = profile;
        this.company = company;
        this.role = role;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getCompany() {
        return company;
    }

    public String getRole() {
        return role;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getDescription() {
        return description;
    }

    public short getSortOrder() {
        return sortOrder;
    }
}
