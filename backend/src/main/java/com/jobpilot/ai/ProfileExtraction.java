package com.jobpilot.ai;

import com.jobpilot.profile.domain.EducationItem;
import com.jobpilot.profile.domain.LanguageItem;
import com.jobpilot.profile.domain.SkillLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Perfil extraído del CV por el LLM ({@link LlmGateway#extractProfile(String)}, F1). Es un borrador: no se guarda
 * hasta que el usuario lo revisa y lo confirma con {@code PUT /profile}. Las longitudes y rangos son los de las
 * columnas de {@code profiles}, {@code profile_skills} y {@code experiences}, y los mismos que valida
 * {@code PUT /profile}: una respuesta que no cabe se rechaza (y se reintenta una vez), nunca se trunca. Las reglas
 * entre campos están en {@link ProfileExtractionValidator}.
 *
 * @param headline        titular profesional, {@code null} si el CV no permite deducirlo
 * @param summary         resumen profesional
 * @param yearsExperience años de experiencia totales
 * @param location        ubicación
 * @param skills          habilidades
 * @param experiences     experiencia laboral, de la más reciente a la más antigua
 * @param education       formación
 * @param languages       idiomas
 */
public record ProfileExtraction(
        @Size(max = 160) String headline,
        @Size(max = ProfileExtraction.MAX_LONG_TEXT) String summary,
        @Min(0) @Max(60) Integer yearsExperience,
        @Size(max = 120) String location,
        @NotNull @Size(max = ProfileExtraction.MAX_SKILLS) List<@NotNull @Valid Skill> skills,
        @NotNull @Size(max = ProfileExtraction.MAX_EXPERIENCES) List<@NotNull @Valid Experience> experiences,
        @NotNull @Size(max = ProfileExtraction.MAX_EDUCATION) List<@NotNull @Valid EducationItem> education,
        @NotNull @Size(max = ProfileExtraction.MAX_LANGUAGES) List<@NotNull @Valid LanguageItem> languages) {

    public static final int MAX_SKILLS = 100;
    public static final int MAX_EXPERIENCES = 50;
    public static final int MAX_EDUCATION = 20;
    public static final int MAX_LANGUAGES = 20;
    public static final int MAX_LONG_TEXT = 4000;

    public ProfileExtraction {
        skills = skills == null ? null : List.copyOf(skills);
        experiences = experiences == null ? null : List.copyOf(experiences);
        education = education == null ? null : List.copyOf(education);
        languages = languages == null ? null : List.copyOf(languages);
    }

    /** Habilidad; el nivel y los años son opcionales. */
    public record Skill(
            @NotBlank @Size(max = 80) String name,
            SkillLevel level,
            @PositiveOrZero @Max(60) Integer years) {
    }

    /** Puesto; {@code endDate} null = puesto actual. */
    public record Experience(
            @NotBlank @Size(max = 120) String company,
            @NotBlank @Size(max = 120) String role,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Size(max = ProfileExtraction.MAX_LONG_TEXT) String description) {
    }
}
