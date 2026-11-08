package com.jobpilot.offer.repository;

import com.jobpilot.offer.domain.JobOffer;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {

    /** Toda lectura de una oferta pasa por aquí: si no es del usuario, no existe (→ 404). */
    Optional<JobOffer> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Bloquea la fila hasta el final de la transacción ({@code SELECT ... FOR UPDATE}), para que dos extracciones
     * simultáneas de la misma oferta no mezclen sus requisitos: la segunda espera y ve la oferta ya COMPLETADA.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM JobOffer o WHERE o.id = :id")
    Optional<JobOffer> findByIdForUpdate(@Param("id") UUID id);

    /** Dashboard (sección 3.2): las ofertas del usuario con la puntuación de su último análisis y su candidatura. */
    @Query(value = """
            SELECT o.id AS id, o.title AS title, o.company AS company, o.created_at AS createdAt,
                   o.extraction_status AS extractionStatus, a.score AS latestScore,
                   a.has_blocking_gap AS hasBlockingGap, ap.status AS applicationStatus
            FROM job_offers o
            LEFT JOIN LATERAL (
                SELECT score, has_blocking_gap
                FROM match_analyses
                WHERE offer_id = o.id
                ORDER BY created_at DESC
                LIMIT 1
            ) a ON true
            LEFT JOIN applications ap ON ap.offer_id = o.id
            WHERE o.user_id = :userId
            ORDER BY o.created_at DESC, o.id""", nativeQuery = true)
    List<OfferSummaryRow> findSummariesByUserId(@Param("userId") UUID userId);
}
