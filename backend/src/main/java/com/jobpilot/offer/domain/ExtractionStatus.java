package com.jobpilot.offer.domain;

/** Estado de la extracción de requisitos con IA. Si falla, la oferta queda en FALLIDA y se puede reintentar. */
public enum ExtractionStatus {
    PENDIENTE,
    COMPLETADA,
    FALLIDA
}
