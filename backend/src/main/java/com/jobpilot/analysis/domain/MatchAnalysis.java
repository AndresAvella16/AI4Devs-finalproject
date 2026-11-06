package com.jobpilot.analysis.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Análisis de encaje perfil ↔ oferta (tabla {@code match_analyses}). La puntuación la calcula
 * MatchScoreCalculator, nunca el LLM. Se guarda un histórico: la UI muestra el más reciente.
 */
@Entity
@Table(name = "match_analyses")
public class MatchAnalysis {

    @Id
    private UUID id;

    private UUID offerId;

    private UUID profileId;

    private short score;

    private boolean hasBlockingGap;

    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> suggestions;

    private String llmModel;

    private Instant createdAt;

    protected MatchAnalysis() {
    }

    public MatchAnalysis(UUID offerId, UUID profileId, short score, boolean hasBlockingGap, String summary,
            List<String> suggestions, String llmModel, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.offerId = offerId;
        this.profileId = profileId;
        this.score = score;
        this.hasBlockingGap = hasBlockingGap;
        this.summary = summary;
        this.suggestions = List.copyOf(suggestions);
        this.llmModel = llmModel;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOfferId() {
        return offerId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public short getScore() {
        return score;
    }

    public boolean isHasBlockingGap() {
        return hasBlockingGap;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getSuggestions() {
        return List.copyOf(suggestions);
    }

    public String getLlmModel() {
        return llmModel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
