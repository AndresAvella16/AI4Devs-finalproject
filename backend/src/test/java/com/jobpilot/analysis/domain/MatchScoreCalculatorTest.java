package com.jobpilot.analysis.domain;

import static com.jobpilot.analysis.domain.MatchResult.CUMPLE;
import static com.jobpilot.analysis.domain.MatchResult.NO_CUMPLE;
import static com.jobpilot.analysis.domain.MatchResult.PARCIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.offer.domain.RequirementType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Fórmula de la sección 1.2: 70 % la media de los obligatorios + 30 % la de los deseables (100 % obligatorios si no
 * hay deseables), redondeo HALF_UP; bloqueante si algún obligatorio es NO_CUMPLE.
 */
class MatchScoreCalculatorTest {

    private final MatchScoreCalculator calculator = new MatchScoreCalculator();

    private static List<RequirementEvaluation> evaluations(List<MatchResult> mandatory, List<MatchResult> desirable) {
        List<RequirementEvaluation> all = new ArrayList<>();
        mandatory.forEach(result -> all.add(new RequirementEvaluation(RequirementType.OBLIGATORIO, result)));
        desirable.forEach(result -> all.add(new RequirementEvaluation(RequirementType.DESEABLE, result)));
        return all;
    }

    private static List<MatchResult> times(int count, MatchResult result) {
        return Collections.nCopies(count, result);
    }

    private static List<MatchResult> concat(List<MatchResult> first, List<MatchResult> second) {
        List<MatchResult> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    static Stream<Arguments> casos() {
        return Stream.of(
                Arguments.of("todo cumple", List.of(CUMPLE, CUMPLE, CUMPLE), List.of(CUMPLE, CUMPLE), 100, false),
                Arguments.of("nada cumple", List.of(NO_CUMPLE, NO_CUMPLE), List.of(NO_CUMPLE), 0, true),
                Arguments.of("ejemplo del readme (CA 1): 70 + 15 = 85",
                        List.of(CUMPLE, CUMPLE, CUMPLE), List.of(PARCIAL, NO_CUMPLE, CUMPLE), 85, false),
                Arguments.of("sin deseables solo cuentan los obligatorios", List.of(CUMPLE, PARCIAL), List.of(), 75,
                        false),
                Arguments.of("un solo requisito parcial", List.of(PARCIAL), List.of(), 50, false),
                Arguments.of("un solo requisito sin cumplir", List.of(NO_CUMPLE), List.of(), 0, true),
                Arguments.of("redondeo HALF_UP: 66,5 + 18 = 84,5 → 85",
                        concat(times(9, CUMPLE), List.of(PARCIAL)), concat(times(3, CUMPLE), times(2, NO_CUMPLE)),
                        85, false),
                Arguments.of("redondeo HALF_UP sin deseables: 12,5 → 13",
                        List.of(PARCIAL, NO_CUMPLE, NO_CUMPLE, NO_CUMPLE), List.of(), 13, true),
                Arguments.of("redondeo hacia abajo: 33,33 → 33", List.of(CUMPLE, NO_CUMPLE, NO_CUMPLE), List.of(),
                        33, true),
                Arguments.of("un obligatorio sin cumplir es bloqueante aunque los deseables sumen (CA 2)",
                        List.of(CUMPLE, NO_CUMPLE), List.of(CUMPLE), 65, true),
                Arguments.of("un deseable sin cumplir no es bloqueante", List.of(CUMPLE), List.of(NO_CUMPLE), 70,
                        false),
                Arguments.of("un obligatorio parcial no es bloqueante", List.of(PARCIAL, CUMPLE), List.of(CUMPLE),
                        83, false),
                Arguments.of("sin obligatorios el 100 % son los deseables", List.of(), List.of(CUMPLE, PARCIAL), 75,
                        false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void calculaLaPuntuacionYElAvisoDeBloqueante(String caso, List<MatchResult> mandatory,
            List<MatchResult> desirable, int score, boolean blocking) {
        assertThat(calculator.calculate(evaluations(mandatory, desirable)))
                .isEqualTo(new ScoreResult(score, blocking));
    }

    @Test
    void elOrdenDeLosRequisitosNoCambiaElResultado() {
        List<RequirementEvaluation> ordered = evaluations(List.of(CUMPLE, PARCIAL), List.of(NO_CUMPLE, CUMPLE));
        List<RequirementEvaluation> reversed = new ArrayList<>(ordered);
        Collections.reverse(reversed);

        assertThat(calculator.calculate(reversed)).isEqualTo(calculator.calculate(ordered));
    }

    @Test
    void unaListaVaciaEsUnError() {
        assertThatThrownBy(() -> calculator.calculate(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaListaNulaEsUnError() {
        assertThatThrownBy(() -> calculator.calculate(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losValoresDeCadaResultadoSonLosDeLaFormula() {
        assertThat(CUMPLE.value()).isEqualTo(1.0);
        assertThat(PARCIAL.value()).isEqualTo(0.5);
        assertThat(NO_CUMPLE.value()).isEqualTo(0.0);
    }

    @Test
    void unaEvaluacionNecesitaTipoYResultado() {
        assertThatThrownBy(() -> new RequirementEvaluation(null, CUMPLE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RequirementEvaluation(RequirementType.OBLIGATORIO, null))
                .isInstanceOf(NullPointerException.class);
    }
}
