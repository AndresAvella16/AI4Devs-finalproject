package com.jobpilot.analysis.service;

import com.jobpilot.analysis.domain.MatchAnalysis;
import com.jobpilot.analysis.repository.RequirementMatchView;
import java.util.List;

/** Un análisis con el resultado de cada requisito, en el orden de la oferta. */
public record AnalysisDetails(MatchAnalysis analysis, List<RequirementMatchView> matches) {

    public AnalysisDetails {
        matches = List.copyOf(matches);
    }
}
