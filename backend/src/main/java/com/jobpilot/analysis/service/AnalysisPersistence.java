package com.jobpilot.analysis.service;

import com.jobpilot.ai.MatchEvaluation;
import com.jobpilot.ai.RequirementSnapshot;
import com.jobpilot.analysis.domain.MatchAnalysis;
import com.jobpilot.analysis.domain.RequirementMatch;
import com.jobpilot.analysis.domain.ScoreResult;
import com.jobpilot.analysis.repository.MatchAnalysisRepository;
import com.jobpilot.analysis.repository.RequirementMatchRepository;
import com.jobpilot.analysis.repository.RequirementMatchView;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las transacciones cortas del análisis. Están en su propio bean para que {@link MatchAnalysisService}, que llama al
 * LLM antes, no tenga ninguna transacción abierta mientras espera (CLAUDE.md).
 */
@Component
class AnalysisPersistence {

    private final MatchAnalysisRepository analyses;
    private final RequirementMatchRepository matches;
    private final Clock clock;

    AnalysisPersistence(MatchAnalysisRepository analyses, RequirementMatchRepository matches, Clock clock) {
        this.analyses = analyses;
        this.matches = matches;
        this.clock = clock;
    }

    /**
     * Guarda el análisis y el resultado de cada requisito en una sola transacción. La evaluación ya está validada
     * (cada requisito exactamente una vez); los textos se guardan sin espacios sobrantes y una evidencia vacía, como
     * {@code null}.
     */
    @Transactional
    AnalysisDetails save(UUID offerId, UUID profileId, List<RequirementSnapshot> requirements,
            MatchEvaluation evaluation, ScoreResult score, String llmModel) {
        MatchAnalysis analysis = analyses.save(new MatchAnalysis(offerId, profileId, (short) score.score(),
                score.hasBlockingGap(), evaluation.summary().strip(),
                evaluation.suggestions().stream().map(String::strip).toList(), llmModel, clock.instant()));

        Map<UUID, MatchEvaluation.Match> byRequirement = evaluation.matches().stream()
                .collect(Collectors.toMap(MatchEvaluation.Match::requirementId, Function.identity()));
        List<RequirementMatch> created = new ArrayList<>();
        List<RequirementMatchView> views = new ArrayList<>();
        for (RequirementSnapshot requirement : requirements) {
            MatchEvaluation.Match match = byRequirement.get(requirement.id());
            String evidence = blankToNull(match.evidence());
            created.add(new RequirementMatch(analysis, requirement.id(), match.result(), evidence));
            views.add(new RequirementMatchView(requirement.id(), requirement.description(), requirement.type(),
                    match.result(), evidence));
        }
        matches.saveAll(created);
        return new AnalysisDetails(analysis, views);
    }

    @Transactional(readOnly = true)
    Optional<AnalysisDetails> latest(UUID offerId) {
        return analyses.findFirstByOfferIdOrderByCreatedAtDescIdDesc(offerId)
                .map(analysis -> new AnalysisDetails(analysis, matches.findViewsByAnalysisId(analysis.getId())));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
