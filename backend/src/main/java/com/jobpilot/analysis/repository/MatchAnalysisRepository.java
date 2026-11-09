package com.jobpilot.analysis.repository;

import com.jobpilot.analysis.domain.MatchAnalysis;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchAnalysisRepository extends JpaRepository<MatchAnalysis, UUID> {

    /** El análisis más reciente de una oferta (índice {@code (offer_id, created_at DESC)}); el id desempata. */
    Optional<MatchAnalysis> findFirstByOfferIdOrderByCreatedAtDescIdDesc(UUID offerId);

    /**
     * Borra los análisis de una oferta; la BD borra en cascada sus {@code requirement_matches}. Se usa al reextraer
     * los requisitos, en la misma transacción que los borra (sección 3.2).
     */
    @Modifying
    @Query("DELETE FROM MatchAnalysis a WHERE a.offerId = :offerId")
    int deleteByOfferId(@Param("offerId") UUID offerId);
}
