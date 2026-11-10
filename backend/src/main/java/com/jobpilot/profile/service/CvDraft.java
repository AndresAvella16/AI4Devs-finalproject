package com.jobpilot.profile.service;

import java.time.Instant;

/**
 * Texto de un CV subido con {@code POST /profile/cv} que todavía no forma parte del perfil. Vive solo en memoria
 * ({@link CvDraftStore}) hasta que el usuario guarda el perfil o caduca.
 *
 * @param fileName  nombre del PDF original, ya limpio
 * @param rawText   texto extraído
 * @param expiresAt a partir de este instante el borrador ya no existe
 */
public record CvDraft(String fileName, String rawText, Instant expiresAt) {
}
