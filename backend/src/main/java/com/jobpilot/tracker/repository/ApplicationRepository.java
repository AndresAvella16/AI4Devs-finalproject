package com.jobpilot.tracker.repository;

import com.jobpilot.tracker.domain.Application;
import com.jobpilot.tracker.domain.ApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    /** Tarjetas del tablero (sección 3.2): la puntuación es la del último análisis de cada oferta. */
    String BOARD_QUERY = """
            SELECT ap.id AS id, ap.offer_id AS offerId, o.title AS offerTitle, o.company AS company,
                   a.score AS score, ap.status AS status, ap.board_position AS boardPosition,
                   ap.applied_at AS appliedAt, ap.follow_up_date AS followUpDate, ap.notes AS notes,
                   ap.version AS version
            FROM applications ap
            JOIN job_offers o ON o.id = ap.offer_id
            LEFT JOIN LATERAL (
                SELECT score
                FROM match_analyses
                WHERE offer_id = ap.offer_id
                ORDER BY created_at DESC
                LIMIT 1
            ) a ON true
            WHERE ap.user_id = :userId""";

    /** Toda lectura de una candidatura pasa por aquí: si no es del usuario, no existe (→ 404). */
    Optional<Application> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByOfferId(UUID offerId);

    /** Las tarjetas de una columna en su orden; empate (solo en datos antiguos) por fecha de creación. */
    @Query("""
            SELECT a FROM Application a
            WHERE a.userId = :userId AND a.status = :status
            ORDER BY a.boardPosition, a.createdAt, a.id""")
    List<Application> findColumn(@Param("userId") UUID userId, @Param("status") ApplicationStatus status);

    /** Posición para una tarjeta nueva: al final de la columna. */
    @Query("""
            SELECT COALESCE(MAX(a.boardPosition) + 1, 0) FROM Application a
            WHERE a.userId = :userId AND a.status = :status""")
    int nextPosition(@Param("userId") UUID userId, @Param("status") ApplicationStatus status);

    /** El tablero en el orden de sus columnas (GUARDADA → DESCARTADA) y, dentro de cada una, por posición. */
    @Query(value = BOARD_QUERY + """

            ORDER BY CASE ap.status
                         WHEN 'GUARDADA' THEN 0
                         WHEN 'ENVIADA' THEN 1
                         WHEN 'ENTREVISTA' THEN 2
                         WHEN 'OFERTA' THEN 3
                         ELSE 4
                     END,
                     ap.board_position, ap.created_at, ap.id""", nativeQuery = true)
    List<ApplicationBoardRow> findBoard(@Param("userId") UUID userId);

    @Query(value = BOARD_QUERY + " AND ap.id = :id", nativeQuery = true)
    Optional<ApplicationBoardRow> findBoardRow(@Param("userId") UUID userId, @Param("id") UUID id);
}
