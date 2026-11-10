package com.jobpilot.profile.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Borradores del CV en memoria ({@code jobpilot.cv-draft.*}, JP-BE-04).
 *
 * @param ttl        tiempo que se guarda el texto de un CV subido hasta que el usuario guarda el perfil (30 min)
 * @param maxEntries borradores como máximo en memoria; al llegar, se olvidan primero los caducados y luego los que
 *                   antes caducan
 */
@Validated
@ConfigurationProperties(prefix = "jobpilot.cv-draft")
public record CvDraftProperties(@NotNull Duration ttl, @Positive int maxEntries) {
}
