package com.jobpilot.offer.repository;

import java.time.Instant;
import java.util.UUID;

/** Fila de la consulta del dashboard ({@link JobOfferRepository#findSummariesByUserId(UUID)}). */
public interface OfferSummaryRow {

    UUID getId();

    String getTitle();

    String getCompany();

    Instant getCreatedAt();

    String getExtractionStatus();

    /** {@code null} si la oferta aún no tiene análisis. */
    Integer getLatestScore();

    Boolean getHasBlockingGap();

    /** {@code null} si la oferta no está en el tablero. */
    String getApplicationStatus();
}
