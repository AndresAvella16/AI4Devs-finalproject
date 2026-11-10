package com.jobpilot.profile.web;

import com.jobpilot.profile.domain.EducationItem;
import com.jobpilot.profile.domain.LanguageItem;
import com.jobpilot.profile.service.ProfileData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Schema {@code ProfileRequest} (sección 4): el perfil completo que se guarda con {@code PUT /profile} y reemplaza
 * al anterior. Todos los campos son opcionales; una lista que no se envía equivale a una vacía. Las longitudes son
 * las de las columnas de la BD. Dos reglas no caben aquí y las comprueba el servicio: las habilidades no se repiten
 * sin distinguir mayúsculas y ninguna experiencia acaba antes de empezar.
 */
public record ProfileRequest(
        @Size(max = 160) String headline,
        @Size(max = ProfileRequest.MAX_LONG_TEXT) String summary,
        @Min(0) @Max(60) Integer yearsExperience,
        @Size(max = 120) String location,
        @Size(max = ProfileRequest.MAX_LANGUAGES) List<@NotNull @Valid LanguageItem> languages,
        @Size(max = ProfileRequest.MAX_EDUCATION) List<@NotNull @Valid EducationItem> education,
        @Size(max = ProfileRequest.MAX_SKILLS) List<@NotNull @Valid SkillDto> skills,
        @Size(max = ProfileRequest.MAX_EXPERIENCES) List<@NotNull @Valid ExperienceDto> experiences) {

    public static final int MAX_SKILLS = 100;
    public static final int MAX_EXPERIENCES = 50;
    public static final int MAX_EDUCATION = 20;
    public static final int MAX_LANGUAGES = 20;
    public static final int MAX_LONG_TEXT = 4000;

    ProfileData toData() {
        return new ProfileData(headline, summary, yearsExperience, location, languages, education,
                skills == null ? null : skills.stream().map(SkillDto::toData).toList(),
                experiences == null ? null : experiences.stream().map(ExperienceDto::toData).toList());
    }
}
