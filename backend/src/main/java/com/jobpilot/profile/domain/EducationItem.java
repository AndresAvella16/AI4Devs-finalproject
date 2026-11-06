package com.jobpilot.profile.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Formación del perfil; elemento del JSONB {@code profiles.education}. El año es opcional. */
public record EducationItem(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 160) String institution,
        Integer year) {
}
