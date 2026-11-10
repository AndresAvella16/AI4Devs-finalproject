package com.jobpilot.profile.service;

import com.jobpilot.profile.domain.EducationItem;
import com.jobpilot.profile.domain.LanguageItem;
import com.jobpilot.profile.domain.SkillLevel;
import java.time.LocalDate;
import java.util.List;

/**
 * Contenido de un perfil, sin identificadores ni datos del CV: lo que el usuario revisa y guarda con
 * {@code PUT /profile} y lo que devuelve el borrador de {@code POST /profile/cv}. Las listas nunca son null.
 *
 * @param experiences en el orden en que se muestran
 */
public record ProfileData(
        String headline,
        String summary,
        Integer yearsExperience,
        String location,
        List<LanguageItem> languages,
        List<EducationItem> education,
        List<Skill> skills,
        List<Experience> experiences) {

    public ProfileData {
        languages = languages == null ? List.of() : List.copyOf(languages);
        education = education == null ? List.of() : List.copyOf(education);
        skills = skills == null ? List.of() : List.copyOf(skills);
        experiences = experiences == null ? List.of() : List.copyOf(experiences);
    }

    /** Habilidad; el nivel y los años son opcionales. */
    public record Skill(String name, SkillLevel level, Integer years) {
    }

    /** Puesto; {@code endDate} null = puesto actual. */
    public record Experience(String company, String role, LocalDate startDate, LocalDate endDate,
            String description) {
    }
}
