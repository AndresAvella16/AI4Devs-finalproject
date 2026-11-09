package com.jobpilot.ai;

import com.jobpilot.analysis.domain.MatchResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Evaluación del encaje devuelta por el LLM ({@link LlmGateway#evaluateMatch}). El modelo solo <b>clasifica</b> cada
 * requisito: la puntuación la calcula {@code MatchScoreCalculator}. Las reglas entre campos (cada requisito
 * exactamente una vez, 3–5 sugerencias) están en {@link MatchEvaluationValidator}.
 *
 * @param matches     un resultado por requisito
 * @param summary     resumen breve del encaje
 * @param suggestions sugerencias accionables (3–5)
 */
public record MatchEvaluation(
        @NotNull List<@NotNull @Valid Match> matches,
        @NotBlank String summary,
        @NotNull List<@NotBlank String> suggestions) {

    public MatchEvaluation {
        matches = matches == null ? null : List.copyOf(matches);
        suggestions = suggestions == null ? null : List.copyOf(suggestions);
    }

    /**
     * Resultado de un requisito.
     *
     * @param requirementId id del requisito, tal como se envió
     * @param result        CUMPLE, PARCIAL o NO_CUMPLE
     * @param evidence      fragmento del perfil que lo justifica; {@code null} si no hay
     */
    public record Match(@NotNull UUID requirementId, @NotNull MatchResult result, String evidence) {
    }
}
