package com.jobpilot.analysis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Resultado de un requisito dentro de un análisis (tabla {@code requirement_matches}). Se crea siempre desde el
 * análisis, que aporta el {@code offerId}. Las dos FKs compuestas de la BD comparten ese {@code offerId}, así que
 * el análisis y el requisito tienen que ser de la misma oferta (sección 3.2).
 */
@Entity
@Table(name = "requirement_matches")
public class RequirementMatch {

    @Id
    private UUID id;

    private UUID analysisId;

    private UUID requirementId;

    private UUID offerId;

    @Enumerated(EnumType.STRING)
    private MatchResult result;

    private String evidence;

    protected RequirementMatch() {
    }

    public RequirementMatch(MatchAnalysis analysis, UUID requirementId, MatchResult result, String evidence) {
        this.id = UUID.randomUUID();
        this.analysisId = analysis.getId();
        this.offerId = analysis.getOfferId();
        this.requirementId = requirementId;
        this.result = result;
        this.evidence = evidence;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public UUID getRequirementId() {
        return requirementId;
    }

    public UUID getOfferId() {
        return offerId;
    }

    public MatchResult getResult() {
        return result;
    }

    public String getEvidence() {
        return evidence;
    }
}
