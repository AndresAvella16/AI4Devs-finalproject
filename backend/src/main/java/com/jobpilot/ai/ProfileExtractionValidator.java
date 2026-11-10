package com.jobpilot.ai;

import java.util.ArrayList;
import java.util.List;

/** Reglas de {@link ProfileExtraction} que Bean Validation no expresa: ninguna experiencia acaba antes de empezar. */
class ProfileExtractionValidator implements ResponseValidator<ProfileExtraction> {

    @Override
    public List<String> validate(ProfileExtraction extraction) {
        List<String> problems = new ArrayList<>();
        List<ProfileExtraction.Experience> experiences = extraction.experiences();
        for (int i = 0; i < experiences.size(); i++) {
            ProfileExtraction.Experience experience = experiences.get(i);
            if (experience.endDate() != null && experience.endDate().isBefore(experience.startDate())) {
                problems.add("experiences[" + i + "]: endDate es anterior a startDate");
            }
        }
        return problems;
    }
}
