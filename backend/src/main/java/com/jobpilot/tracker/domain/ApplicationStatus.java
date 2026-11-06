package com.jobpilot.tracker.domain;

/** Columna del tablero. Las transiciones permitidas las define ApplicationStatusPolicy. */
public enum ApplicationStatus {
    GUARDADA,
    ENVIADA,
    ENTREVISTA,
    OFERTA,
    DESCARTADA
}
