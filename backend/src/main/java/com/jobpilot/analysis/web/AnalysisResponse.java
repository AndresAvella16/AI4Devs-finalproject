package com.jobpilot.analysis.web;

import com.jobpilot.analysis.domain.MatchAnalysis;
import com.jobpilot.analysis.service.AnalysisDetails;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code AnalysisResponse} de la sección 4. */
public record AnalysisResponse(
        UUID id,
        UUID offerId,
        int score,
        boolean hasBlockingGap,
        String summary,
        List<RequirementMatchResponse> matches,
        List<String> suggestions,
        Instant createdAt) {

    static AnalysisResponse from(AnalysisDetails details) {
        MatchAnalysis analysis = details.analysis();
        return new AnalysisResponse(analysis.getId(), analysis.getOfferId(), analysis.getScore(),
                analysis.isHasBlockingGap(), analysis.getSummary(),
                details.matches().stream().map(RequirementMatchResponse::from).toList(),
                analysis.getSuggestions(), analysis.getCreatedAt());
    }
}
