package com.jobpilot.profile.web;

import com.jobpilot.profile.domain.EducationItem;
import com.jobpilot.profile.domain.LanguageItem;
import com.jobpilot.profile.domain.Profile;
import com.jobpilot.profile.service.ProfileDetails;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Schema {@code ProfileResponse} (sección 4): el perfil guardado. Las habilidades van por nombre y las experiencias
 * en su orden. {@code cvFileName} es null si el perfil se ha hecho a mano.
 */
public record ProfileResponse(
        UUID id,
        String headline,
        String summary,
        Integer yearsExperience,
        String location,
        List<LanguageItem> languages,
        List<EducationItem> education,
        List<SkillDto> skills,
        List<ExperienceDto> experiences,
        String cvFileName,
        Instant updatedAt) {

    static ProfileResponse from(ProfileDetails details) {
        Profile profile = details.profile();
        return new ProfileResponse(profile.getId(), profile.getHeadline(), profile.getSummary(),
                profile.getYearsExperience() == null ? null : profile.getYearsExperience().intValue(),
                profile.getLocation(), profile.getLanguages(), profile.getEducation(),
                details.skills().stream().map(SkillDto::from).toList(),
                details.experiences().stream().map(ExperienceDto::from).toList(),
                profile.getCvFileName(), profile.getUpdatedAt());
    }
}
