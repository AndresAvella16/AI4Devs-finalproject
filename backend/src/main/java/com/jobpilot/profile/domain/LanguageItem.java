package com.jobpilot.profile.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Idioma del perfil; elemento del JSONB {@code profiles.languages}. */
public record LanguageItem(
        @NotBlank @Size(max = 60) String language,
        @NotBlank @Size(max = 40) String level) {
}
