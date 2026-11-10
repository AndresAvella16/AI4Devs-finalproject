package com.jobpilot.profile.web;

import com.jobpilot.profile.domain.Experience;
import com.jobpilot.profile.service.ProfileData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Schema {@code Experience} (sección 4). {@code endDate} null = puesto actual; nunca anterior a {@code startDate}. */
public record ExperienceDto(
        @NotBlank @Size(max = 120) String company,
        @NotBlank @Size(max = 120) String role,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Size(max = ProfileRequest.MAX_LONG_TEXT) String description) {

    ProfileData.Experience toData() {
        return new ProfileData.Experience(company, role, startDate, endDate, description);
    }

    static ExperienceDto from(ProfileData.Experience experience) {
        return new ExperienceDto(experience.company(), experience.role(), experience.startDate(),
                experience.endDate(), experience.description());
    }

    static ExperienceDto from(Experience experience) {
        return new ExperienceDto(experience.getCompany(), experience.getRole(), experience.getStartDate(),
                experience.getEndDate(), experience.getDescription());
    }
}
