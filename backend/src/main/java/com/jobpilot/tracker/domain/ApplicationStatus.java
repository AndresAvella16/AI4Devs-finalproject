package com.jobpilot.tracker.domain;

/** Columna del tablero, en el orden en que se muestran. Las transiciones permitidas las define ApplicationStatusPolicy. */
public enum ApplicationStatus {
    GUARDADA,
    ENVIADA,
    ENTREVISTA,
    OFERTA,
    DESCARTADA;

    /** La candidatura ya se ha enviado: {@code applied_at} es obligatoria ({@code ck_applications_applied_at}). */
    public boolean requiresAppliedAt() {
        return this == ENVIADA || this == ENTREVISTA || this == OFERTA;
    }

    /** OFERTA y DESCARTADA cierran el proceso: ya no hay seguimiento pendiente. */
    public boolean isClosed() {
        return this == OFERTA || this == DESCARTADA;
    }
}
