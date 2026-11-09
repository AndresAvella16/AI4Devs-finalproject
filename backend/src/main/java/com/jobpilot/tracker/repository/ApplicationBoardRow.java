package com.jobpilot.tracker.repository;

import java.time.LocalDate;
import java.util.UUID;

/** Tarjeta del tablero: la candidatura con el puesto y la empresa de su oferta y la puntuación de su último análisis. */
public interface ApplicationBoardRow {

    UUID getId();

    UUID getOfferId();

    /** {@code null} si la extracción de la oferta está PENDIENTE o FALLIDA. */
    String getOfferTitle();

    String getCompany();

    /** {@code null} si la oferta aún no tiene análisis. */
    Integer getScore();

    String getStatus();

    int getBoardPosition();

    LocalDate getAppliedAt();

    LocalDate getFollowUpDate();

    String getNotes();

    long getVersion();
}
