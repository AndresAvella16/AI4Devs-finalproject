package com.jobpilot.analysis.repository;

import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.offer.domain.RequirementType;
import java.util.UUID;

/** Resultado de un requisito junto con la descripción y el tipo del requisito, para la respuesta de la API. */
public record RequirementMatchView(UUID requirementId, String description, RequirementType type, MatchResult result,
        String evidence) {
}
