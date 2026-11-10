package com.jobpilot.profile.web;

import com.jobpilot.profile.domain.EducationItem;
import com.jobpilot.profile.domain.LanguageItem;
import com.jobpilot.profile.service.CvDraftResult;
import com.jobpilot.profile.service.ProfileData;
import java.util.List;

/**
 * Schema {@code CvDraftResponse} (sección 4): el borrador que ha extraído la IA del CV, con la forma de
 * {@code ProfileRequest} para prerrellenar el formulario. No se guarda como perfil hasta que el usuario lo confirma
 * con {@code PUT /profile}.
 */
public record CvDraftResponse(
        String headline,
        String summary,
        Integer yearsExperience,
        String location,
        List<LanguageItem> languages,
        List<EducationItem> education,
        List<SkillDto> skills,
        List<ExperienceDto> experiences,
        String cvFileName) {

    static CvDraftResponse from(CvDraftResult result) {
        ProfileData draft = result.draft();
        return new CvDraftResponse(draft.headline(), draft.summary(), draft.yearsExperience(), draft.location(),
                draft.languages(), draft.education(), draft.skills().stream().map(SkillDto::from).toList(),
                draft.experiences().stream().map(ExperienceDto::from).toList(), result.cvFileName());
    }
}
