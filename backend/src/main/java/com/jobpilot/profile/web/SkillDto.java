package com.jobpilot.profile.web;

import com.jobpilot.profile.domain.ProfileSkill;
import com.jobpilot.profile.domain.SkillLevel;
import com.jobpilot.profile.service.ProfileData;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Schema {@code Skill} (sección 4). El nombre es único en el perfil sin distinguir mayúsculas. */
public record SkillDto(
        @NotBlank @Size(max = 80) String name,
        SkillLevel level,
        @PositiveOrZero @Max(60) Integer years) {

    ProfileData.Skill toData() {
        return new ProfileData.Skill(name, level, years);
    }

    static SkillDto from(ProfileData.Skill skill) {
        return new SkillDto(skill.name(), skill.level(), skill.years());
    }

    static SkillDto from(ProfileSkill skill) {
        return new SkillDto(skill.getName(), skill.getLevel(),
                skill.getYears() == null ? null : skill.getYears().intValue());
    }
}
