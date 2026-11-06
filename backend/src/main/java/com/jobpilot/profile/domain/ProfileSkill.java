package com.jobpilot.profile.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** Habilidad del perfil (tabla {@code profile_skills}). El nombre es único por perfil sin distinguir mayúsculas. */
@Entity
@Table(name = "profile_skills")
public class ProfileSkill {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    private String name;

    @Enumerated(EnumType.STRING)
    private SkillLevel level;

    private Short years;

    protected ProfileSkill() {
    }

    public ProfileSkill(Profile profile, String name, SkillLevel level, Short years) {
        this.id = UUID.randomUUID();
        this.profile = profile;
        this.name = name;
        this.level = level;
        this.years = years;
    }

    public UUID getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getName() {
        return name;
    }

    public SkillLevel getLevel() {
        return level;
    }

    public Short getYears() {
        return years;
    }
}
