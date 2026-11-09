package com.jobpilot.analysis.repository;

import com.jobpilot.analysis.domain.RequirementMatch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequirementMatchRepository extends JpaRepository<RequirementMatch, UUID> {

    /** Detalle de un análisis, en el orden de los requisitos en la oferta. */
    @Query("""
            SELECT new com.jobpilot.analysis.repository.RequirementMatchView(
                       m.requirementId, r.description, r.type, m.result, m.evidence)
            FROM RequirementMatch m JOIN OfferRequirement r ON r.id = m.requirementId
            WHERE m.analysisId = :analysisId
            ORDER BY r.sortOrder""")
    List<RequirementMatchView> findViewsByAnalysisId(@Param("analysisId") UUID analysisId);
}
