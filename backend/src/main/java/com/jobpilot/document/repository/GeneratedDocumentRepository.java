package com.jobpilot.document.repository;

import com.jobpilot.document.domain.DocumentType;
import com.jobpilot.document.domain.GeneratedDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Las consultas por oferta y tipo usan el índice único {@code (offer_id, type, version)}. */
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {

    /** El documento, solo si su oferta es del usuario (si no → 404, nunca 403). */
    @Query("""
            SELECT d FROM GeneratedDocument d
            WHERE d.id = :id
              AND EXISTS (SELECT 1 FROM JobOffer o WHERE o.id = d.offerId AND o.userId = :userId)""")
    Optional<GeneratedDocument> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /** La versión más alta de un documento, o null si aún no hay ninguna. */
    @Query("SELECT max(d.version) FROM GeneratedDocument d WHERE d.offerId = :offerId AND d.type = :type")
    Short findMaxVersion(@Param("offerId") UUID offerId, @Param("type") DocumentType type);

    /** La última versión completa. */
    Optional<GeneratedDocument> findFirstByOfferIdAndTypeOrderByVersionDesc(UUID offerId, DocumentType type);

    /** Todas las versiones sin el contenido, de la más reciente a la más antigua. */
    @Query("""
            SELECT new com.jobpilot.document.repository.DocumentVersionView(d.id, d.version, d.editedByUser,
                       d.createdAt)
            FROM GeneratedDocument d
            WHERE d.offerId = :offerId AND d.type = :type
            ORDER BY d.version DESC""")
    List<DocumentVersionView> findVersions(@Param("offerId") UUID offerId, @Param("type") DocumentType type);
}
