package com.jobpilot.analysis.domain;

import com.jobpilot.offer.domain.RequirementType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Puntuación de encaje (sección 1.2). Java puro, sin Spring: el LLM solo clasifica los requisitos y el número que ve
 * el usuario sale siempre de aquí, así que es reproducible.
 *
 * <pre>
 * obligatorios = media de los valores de los requisitos OBLIGATORIOS   (CUMPLE = 1, PARCIAL = 0,5, NO_CUMPLE = 0)
 * deseables    = media de los valores de los requisitos DESEABLES
 *
 * con obligatorios y deseables: score = round(100 × (0,7 × obligatorios + 0,3 × deseables))
 * sin deseables:                score = round(100 × obligatorios)
 * sin obligatorios:             score = round(100 × deseables)
 * </pre>
 *
 * <p>El redondeo es {@code HALF_UP} (84,5 → 85) y se hace sobre el valor exacto: se opera con {@link BigDecimal} y
 * una sola división final, sin errores de coma flotante. Es bloqueante si algún obligatorio es NO_CUMPLE.
 */
public final class MatchScoreCalculator {

    private static final BigDecimal MANDATORY_WEIGHT = new BigDecimal("70");
    private static final BigDecimal DESIRABLE_WEIGHT = new BigDecimal("30");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public ScoreResult calculate(List<RequirementEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            throw new IllegalArgumentException("Hace falta al menos un requisito para calcular el encaje");
        }
        Totals mandatory = totals(evaluations, RequirementType.OBLIGATORIO);
        Totals desirable = totals(evaluations, RequirementType.DESEABLE);

        BigDecimal numerator;
        BigDecimal denominator;
        if (desirable.count() == 0) {
            numerator = HUNDRED.multiply(mandatory.sum());
            denominator = BigDecimal.valueOf(mandatory.count());
        } else if (mandatory.count() == 0) {
            numerator = HUNDRED.multiply(desirable.sum());
            denominator = BigDecimal.valueOf(desirable.count());
        } else {
            // 70 × sumaO / nO + 30 × sumaD / nD, con denominador común nO × nD
            numerator = MANDATORY_WEIGHT.multiply(mandatory.sum()).multiply(BigDecimal.valueOf(desirable.count()))
                    .add(DESIRABLE_WEIGHT.multiply(desirable.sum()).multiply(BigDecimal.valueOf(mandatory.count())));
            denominator = BigDecimal.valueOf((long) mandatory.count() * desirable.count());
        }
        int score = numerator.divide(denominator, 0, RoundingMode.HALF_UP).intValueExact();

        boolean blocking = evaluations.stream().anyMatch(evaluation ->
                evaluation.type() == RequirementType.OBLIGATORIO && evaluation.result() == MatchResult.NO_CUMPLE);
        return new ScoreResult(score, blocking);
    }

    private static Totals totals(List<RequirementEvaluation> evaluations, RequirementType type) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (RequirementEvaluation evaluation : evaluations) {
            if (evaluation.type() == type) {
                sum = sum.add(BigDecimal.valueOf(evaluation.result().value()));
                count++;
            }
        }
        return new Totals(sum, count);
    }

    private record Totals(BigDecimal sum, int count) {
    }
}
