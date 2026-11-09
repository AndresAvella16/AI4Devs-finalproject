package com.jobpilot.analysis.domain;

/**
 * Puntuación de encaje.
 *
 * @param score          0–100
 * @param hasBlockingGap algún requisito obligatorio es NO_CUMPLE ("requisito bloqueante")
 */
public record ScoreResult(int score, boolean hasBlockingGap) {
}
