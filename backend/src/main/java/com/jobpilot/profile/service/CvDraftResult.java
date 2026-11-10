package com.jobpilot.profile.service;

/** Resultado de {@code POST /profile/cv}: el borrador del perfil que ha estructurado la IA. */
public record CvDraftResult(String cvFileName, ProfileData draft) {
}
