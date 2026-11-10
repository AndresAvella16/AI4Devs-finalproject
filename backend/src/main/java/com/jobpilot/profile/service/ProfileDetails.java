package com.jobpilot.profile.service;

import com.jobpilot.profile.domain.Experience;
import com.jobpilot.profile.domain.Profile;
import com.jobpilot.profile.domain.ProfileSkill;
import java.util.List;

/**
 * Un perfil con sus habilidades (por nombre) y sus experiencias (en su orden), ya cargados.
 */
public record ProfileDetails(Profile profile, List<ProfileSkill> skills, List<Experience> experiences) {

    public ProfileDetails {
        skills = List.copyOf(skills);
        experiences = List.copyOf(experiences);
    }
}
