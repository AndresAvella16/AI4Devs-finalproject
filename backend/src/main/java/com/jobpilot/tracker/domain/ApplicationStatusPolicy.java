package com.jobpilot.tracker.domain;

/**
 * Reglas de transición de la HU-05 (Java puro). La base de datos no las expresa (necesitarían un trigger):
 *
 * <ul>
 *   <li>Solo se avanza al estado siguiente: GUARDADA → ENVIADA → ENTREVISTA → OFERTA.</li>
 *   <li>Desde cualquier estado distinto de DESCARTADA se puede descartar (también desde OFERTA, si el candidato la
 *       rechaza).</li>
 *   <li>DESCARTADA → GUARDADA reabre la candidatura.</li>
 *   <li>No se retrocede ni se saltan estados; quedarse en el mismo estado tampoco es una transición.</li>
 * </ul>
 */
public final class ApplicationStatusPolicy {

    private ApplicationStatusPolicy() {
    }

    public static boolean isAllowed(ApplicationStatus from, ApplicationStatus to) {
        return switch (from) {
            case GUARDADA -> to == ApplicationStatus.ENVIADA || to == ApplicationStatus.DESCARTADA;
            case ENVIADA -> to == ApplicationStatus.ENTREVISTA || to == ApplicationStatus.DESCARTADA;
            case ENTREVISTA -> to == ApplicationStatus.OFERTA || to == ApplicationStatus.DESCARTADA;
            case OFERTA -> to == ApplicationStatus.DESCARTADA;
            case DESCARTADA -> to == ApplicationStatus.GUARDADA;
        };
    }
}
