package com.jobpilot.analysis.domain;

/** Resultado de un requisito en el análisis, con el valor que aporta a la puntuación (sección 1.2). */
public enum MatchResult {
    CUMPLE(1.0),
    PARCIAL(0.5),
    NO_CUMPLE(0.0);

    private final double value;

    MatchResult(double value) {
        this.value = value;
    }

    /** CUMPLE = 1, PARCIAL = 0,5, NO_CUMPLE = 0. */
    public double value() {
        return value;
    }
}
