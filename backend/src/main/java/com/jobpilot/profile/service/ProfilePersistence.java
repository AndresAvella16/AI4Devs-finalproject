package com.jobpilot.profile.service;

import com.jobpilot.profile.domain.EducationItem;
import com.jobpilot.profile.domain.Experience;
import com.jobpilot.profile.domain.LanguageItem;
import com.jobpilot.profile.domain.Profile;
import com.jobpilot.profile.domain.ProfileSkill;
import com.jobpilot.profile.repository.ExperienceRepository;
import com.jobpilot.profile.repository.ProfileRepository;
import com.jobpilot.profile.repository.ProfileSkillRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las transacciones cortas del perfil. Están en su propio bean para que {@link ProfileService}, que llama al LLM al
 * subir el CV, nunca tenga una transacción abierta mientras espera (CLAUDE.md).
 */
@Component
class ProfilePersistence {

    private final ProfileRepository profiles;
    private final ProfileSkillRepository skills;
    private final ExperienceRepository experiences;
    private final Clock clock;

    ProfilePersistence(ProfileRepository profiles, ProfileSkillRepository skills, ExperienceRepository experiences,
            Clock clock) {
        this.profiles = profiles;
        this.skills = skills;
        this.experiences = experiences;
        this.clock = clock;
    }

    /**
     * Crea el perfil o lo reemplaza entero en una sola transacción: datos, habilidades y experiencias. Al
     * reemplazarlo, el id se conserva (y con él los análisis) y las habilidades y experiencias anteriores se borran
     * antes de insertar las nuevas. Si hay borrador del CV, su texto y su nombre pasan al perfil; si no, se conservan
     * los que tuviera (o quedan vacíos en un perfil nuevo hecho a mano).
     *
     * @throws org.springframework.dao.DataIntegrityViolationException si otra petición ha creado el perfil a la vez
     *         ({@code uq_profiles_user_id}); el {@code INSERT} se ejecuta en el momento para detectarlo aquí
     */
    @Transactional
    SavedProfile save(UUID userId, ProfileData data, CvDraft draft) {
        Instant now = clock.instant();
        Optional<Profile> existing = profiles.findByUserId(userId);
        Profile profile;
        if (existing.isPresent()) {
            profile = existing.get();
            profile.replace(blankToNull(data.headline()), blankToNull(data.summary()), toShort(data.yearsExperience()),
                    blankToNull(data.location()), languages(data), education(data), now);
            if (draft != null) {
                profile.attachCv(draft.fileName(), draft.rawText(), now);
            }
            skills.deleteByProfileId(profile.getId());
            experiences.deleteByProfileId(profile.getId());
        } else {
            profile = profiles.saveAndFlush(new Profile(userId, blankToNull(data.headline()),
                    blankToNull(data.summary()), toShort(data.yearsExperience()), blankToNull(data.location()),
                    languages(data), education(data), draft == null ? null : draft.fileName(),
                    draft == null ? null : draft.rawText(), now));
        }

        List<ProfileSkill> newSkills = new ArrayList<>();
        for (ProfileData.Skill skill : data.skills()) {
            newSkills.add(new ProfileSkill(profile, skill.name().strip(), skill.level(), toShort(skill.years())));
        }
        skills.saveAll(newSkills);

        List<Experience> newExperiences = new ArrayList<>();
        short order = 0;
        for (ProfileData.Experience experience : data.experiences()) {
            newExperiences.add(new Experience(profile, experience.company().strip(), experience.role().strip(),
                    experience.startDate(), experience.endDate(), blankToNull(experience.description()), order++));
        }
        List<Experience> savedExperiences = experiences.saveAll(newExperiences);

        // Las habilidades se leen de nuevo para devolverlas en el mismo orden que GET /profile (lower(name) en la BD).
        return new SavedProfile(new ProfileDetails(profile, skills.findByProfileIdOrdered(profile.getId()),
                savedExperiences), existing.isEmpty());
    }

    @Transactional(readOnly = true)
    Optional<ProfileDetails> details(UUID userId) {
        return profiles.findByUserId(userId).map(profile -> new ProfileDetails(profile,
                skills.findByProfileIdOrdered(profile.getId()), experiences.findByProfileIdOrdered(profile.getId())));
    }

    private static List<LanguageItem> languages(ProfileData data) {
        return data.languages().stream()
                .map(item -> new LanguageItem(item.language().strip(), item.level().strip()))
                .toList();
    }

    private static List<EducationItem> education(ProfileData data) {
        return data.education().stream()
                .map(item -> new EducationItem(item.title().strip(), blankToNull(item.institution()), item.year()))
                .toList();
    }

    private static Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
