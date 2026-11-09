package com.jobpilot.ai;

import com.jobpilot.profile.domain.EducationItem;
import com.jobpilot.profile.domain.LanguageItem;
import com.jobpilot.profile.domain.SkillLevel;
import java.time.LocalDate;
import java.util.List;

/**
 * El perfil tal como se envía al LLM para evaluar el encaje (F3). Solo el perfil estructurado que ha revisado el
 * usuario: ni el texto en bruto del CV, ni el nombre ni el email (sección 2.5, "nada que no haga falta").
 *
 * @param headline        titular profesional
 * @param summary         resumen
 * @param yearsExperience años de experiencia
 * @param location        ubicación
 * @param skills          habilidades
 * @param experiences     experiencia laboral, en el orden del perfil
 * @param education       formación
 * @param languages       idiomas
 */
public record ProfileSnapshot(
        String headline,
        String summary,
        Short yearsExperience,
        String location,
        List<Skill> skills,
        List<ExperienceItem> experiences,
        List<EducationItem> education,
        List<LanguageItem> languages) {

    public ProfileSnapshot {
        skills = List.copyOf(skills);
        experiences = List.copyOf(experiences);
        education = List.copyOf(education);
        languages = List.copyOf(languages);
    }

    /** Habilidad; el nivel y los años son opcionales. */
    public record Skill(String name, SkillLevel level, Short years) {
    }

    /** Puesto; {@code endDate} null = puesto actual. */
    public record ExperienceItem(String company, String role, LocalDate startDate, LocalDate endDate,
            String description) {
    }
}
