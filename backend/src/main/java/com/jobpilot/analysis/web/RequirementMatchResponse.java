package com.jobpilot.analysis.web;

import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.analysis.repository.RequirementMatchView;
import com.jobpilot.offer.domain.RequirementType;
import java.util.UUID;

/** {@code RequirementMatch} de la sección 4. */
public record RequirementMatchResponse(UUID requirementId, String description, RequirementType type,
        MatchResult result, String evidence) {

    static RequirementMatchResponse from(RequirementMatchView view) {
        return new RequirementMatchResponse(view.requirementId(), view.description(), view.type(), view.result(),
                view.evidence());
    }
}
