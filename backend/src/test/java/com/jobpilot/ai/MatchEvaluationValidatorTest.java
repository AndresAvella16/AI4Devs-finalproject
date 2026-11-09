package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.analysis.domain.MatchResult;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Reglas de la evaluación del LLM: cada requisito exactamente una vez y entre 3 y 5 sugerencias. */
class MatchEvaluationValidatorTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    private final MatchEvaluationValidator validator = new MatchEvaluationValidator(List.of(A, B, C));

    private static MatchEvaluation.Match match(UUID id) {
        return new MatchEvaluation.Match(id, MatchResult.CUMPLE, "Evidencia");
    }

    private static MatchEvaluation evaluation(List<UUID> ids, int suggestions) {
        return new MatchEvaluation(ids.stream().map(MatchEvaluationValidatorTest::match).toList(), "Resumen",
                Collections.nCopies(suggestions, "Sugerencia"));
    }

    @Test
    void unaEvaluacionCompletaEsValida() {
        assertThat(validator.validate(evaluation(List.of(A, B, C), 3))).isEmpty();
    }

    @Test
    void elOrdenDeLosRequisitosDaIgual() {
        assertThat(validator.validate(evaluation(List.of(C, A, B), 3))).isEmpty();
    }

    @Test
    void faltaUnRequisito() {
        assertThat(validator.validate(evaluation(List.of(A, B), 3)))
                .singleElement().asString().contains("falta").contains(C.toString());
    }

    @Test
    void unRequisitoRepetido() {
        assertThat(validator.validate(evaluation(List.of(A, B, C, B), 3)))
                .singleElement().asString().contains("repetido").contains(B.toString());
    }

    @Test
    void unRequisitoDesconocido() {
        UUID unknown = UUID.randomUUID();
        assertThat(validator.validate(evaluation(List.of(A, B, C, unknown), 3)))
                .singleElement().asString().contains("desconocido").contains(unknown.toString());
    }

    @Test
    void unIdDesconocidoEnLugarDeUnoQueFaltaDaLosDosProblemas() {
        assertThat(validator.validate(evaluation(List.of(A, B, UUID.randomUUID()), 3))).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 6, 10})
    void pocasODemasiadasSugerencias(int count) {
        assertThat(validator.validate(evaluation(List.of(A, B, C), count)))
                .singleElement().asString().contains("suggestions").contains(String.valueOf(count));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void entreTresYCincoSugerenciasEsValido(int count) {
        assertThat(validator.validate(evaluation(List.of(A, B, C), count))).isEmpty();
    }

    @Test
    void losProblemasNoCopianElContenidoDeLaRespuesta() {
        MatchEvaluation evaluation = new MatchEvaluation(
                List.of(new MatchEvaluation.Match(A, MatchResult.CUMPLE, "dato personal del CV")), "Resumen privado",
                List.of("sugerencia privada"));

        assertThat(validator.validate(evaluation))
                .isNotEmpty()
                .noneMatch(problem -> problem.contains("privad") || problem.contains("personal"));
    }
}
