package com.jobpilot.document.service;

import com.jobpilot.document.domain.DocumentType;
import com.jobpilot.document.domain.GeneratedDocument;
import com.jobpilot.document.repository.GeneratedDocumentRepository;
import com.jobpilot.shared.error.ConflictException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las transacciones cortas de los documentos. Están en su propio bean para que {@link DocumentService}, que llama al
 * LLM antes, no tenga ninguna transacción abierta mientras espera (CLAUDE.md).
 *
 * <p>El número de versión lo protege {@code uq_generated_documents_offer_type_version}: si otra escritura ha creado
 * esa versión antes, la BD rechaza el {@code INSERT} y se responde 409 {@code document-version-conflict}. Por eso se
 * inserta con {@code saveAndFlush}: el choque se detecta aquí, dentro de la transacción, y no al confirmarla.
 */
@Component
class DocumentPersistence {

    static final String VERSION_CONFLICT = "document-version-conflict";

    private static final String VERSION_CONSTRAINT = "uq_generated_documents_offer_type_version";

    private final GeneratedDocumentRepository documents;
    private final Clock clock;

    DocumentPersistence(GeneratedDocumentRepository documents, Clock clock) {
        this.documents = documents;
        this.clock = clock;
    }

    /** La versión que se quería crear ya existe: otra edición o regeneración se ha adelantado. */
    static ConflictException versionConflict() {
        return new ConflictException(VERSION_CONFLICT, "El documento ha cambiado",
                "Se ha guardado otra versión de este documento desde que lo abriste. Recárgalo y vuelve a aplicar "
                        + "tus cambios.");
    }

    /** Documento generado por la IA: la versión siguiente a la más alta (o la 1), con {@code editedByUser = false}. */
    @Transactional
    GeneratedDocument saveGenerated(UUID offerId, DocumentType type, String contentMarkdown) {
        Short max = documents.findMaxVersion(offerId, type);
        return insert(new GeneratedDocument(offerId, type, contentMarkdown, nextVersion(max == null ? 0 : max), false,
                clock.instant()));
    }

    /**
     * Edición del usuario a partir de {@code base}: la versión {@code base + 1}, con {@code editedByUser = true}. Si
     * {@code base} ya no era la última, esa versión existe y es 409 (bloqueo optimista).
     */
    @Transactional
    GeneratedDocument saveEdit(GeneratedDocument base, String contentMarkdown) {
        return insert(new GeneratedDocument(base.getOfferId(), base.getType(), contentMarkdown,
                nextVersion(base.getVersion()), true, clock.instant()));
    }

    /** La última versión completa y la lista de versiones, o vacío si aún no hay ninguna de ese tipo. */
    @Transactional(readOnly = true)
    Optional<DocumentHistory> history(UUID offerId, DocumentType type) {
        return documents.findFirstByOfferIdAndTypeOrderByVersionDesc(offerId, type)
                .map(latest -> new DocumentHistory(latest, documents.findVersions(offerId, type)));
    }

    private GeneratedDocument insert(GeneratedDocument document) {
        try {
            return documents.saveAndFlush(document);
        } catch (DataIntegrityViolationException e) {
            if (violates(e, VERSION_CONSTRAINT)) {
                throw versionConflict();
            }
            throw e;
        }
    }

    private static short nextVersion(int current) {
        if (current >= Short.MAX_VALUE) {
            throw new IllegalStateException("El documento ha llegado al máximo de versiones");
        }
        return (short) (current + 1);
    }

    private static boolean violates(Throwable error, String constraint) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
