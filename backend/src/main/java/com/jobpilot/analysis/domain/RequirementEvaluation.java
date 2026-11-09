package com.jobpilot.analysis.domain;

import com.jobpilot.offer.domain.RequirementType;
import java.util.Objects;

/** Lo que necesita {@link MatchScoreCalculator} de cada requisito: si es obligatorio y qué resultado ha tenido. */
public record RequirementEvaluation(RequirementType type, MatchResult result) {

    public RequirementEvaluation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(result, "result");
    }
}
