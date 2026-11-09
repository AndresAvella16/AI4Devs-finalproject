package com.jobpilot.ai;

import static com.jobpilot.analysis.domain.MatchResult.CUMPLE;
import static com.jobpilot.analysis.domain.MatchResult.NO_CUMPLE;
import static com.jobpilot.analysis.domain.MatchResult.PARCIAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.offer.domain.RequirementCategory;
import com.jobpilot.offer.domain.RequirementType;
import jakarta.validation.Validation;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * El fake del encaje: los resultados de {@code fake-llm/match-evaluation.json} van por posición y se asignan a los
 * ids reales de los requisitos, que se generan en cada ejecución. Con la oferta fake (Ejemplo 1) sale el 85 del
 * readme.
 */
class FakeLlmGatewayTest {

    private static final ProfileSnapshot PROFILE = new ProfileSnapshot(null, null, null, null, List.of(), List.of(),
            List.of(), List.of());

    private final FakeLlmGateway gateway = new FakeLlmGateway(new FakeResponses());

    private static List<RequirementSnapshot> requirements(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new RequirementSnapshot(UUID.randomUUID(), "Requisito " + i,
                        i < 3 ? RequirementType.OBLIGATORIO : RequirementType.DESEABLE, RequirementCategory.TECNICO))
                .toList();
    }

    private static List<UUID> ids(List<RequirementSnapshot> requirements) {
        return requirements.stream().map(RequirementSnapshot::id).toList();
    }

    private static List<MatchResult> results(MatchEvaluation evaluation) {
        return evaluation.matches().stream().map(MatchEvaluation.Match::result).toList();
    }

    private static void assertValid(MatchEvaluation evaluation, List<RequirementSnapshot> requirements) {
        assertThat(Validation.buildDefaultValidatorFactory().getValidator().validate(evaluation)).isEmpty();
        assertThat(new MatchEvaluationValidator(ids(requirements)).validate(evaluation)).isEmpty();
    }

    @Test
    void conLosSeisRequisitosDeLaOfertaFakeDaLosResultadosDelEjemplo2() {
        List<RequirementSnapshot> requirements = requirements(6);

        MatchEvaluation evaluation = gateway.evaluateMatch(PROFILE, requirements);

        assertThat(evaluation.matches()).extracting(MatchEvaluation.Match::requirementId)
                .containsExactlyElementsOf(ids(requirements));
        assertThat(results(evaluation)).containsExactly(CUMPLE, CUMPLE, CUMPLE, PARCIAL, NO_CUMPLE, CUMPLE);
        assertThat(evaluation.suggestions()).hasSizeBetween(3, 5);
        assertValid(evaluation, requirements);
    }

    @Test
    void conMenosRequisitosUsaLosPrimerosResultados() {
        List<RequirementSnapshot> requirements = requirements(2);

        MatchEvaluation evaluation = gateway.evaluateMatch(PROFILE, requirements);

        assertThat(results(evaluation)).containsExactly(CUMPLE, CUMPLE);
        assertValid(evaluation, requirements);
    }

    @Test
    void conMasRequisitosRepiteLaSecuencia() {
        List<RequirementSnapshot> requirements = requirements(8);

        MatchEvaluation evaluation = gateway.evaluateMatch(PROFILE, requirements);

        assertThat(results(evaluation)).containsExactly(CUMPLE, CUMPLE, CUMPLE, PARCIAL, NO_CUMPLE, CUMPLE, CUMPLE,
                CUMPLE);
        assertValid(evaluation, requirements);
    }
}
