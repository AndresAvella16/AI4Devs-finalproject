package com.jobpilot.document.web;

import com.jobpilot.document.service.DocumentHistory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** La última versión completa y todas las versiones sin el contenido, de la más reciente a la más antigua. */
public record DocumentHistoryResponse(DocumentResponse latest, List<Version> versions) {

    public DocumentHistoryResponse {
        versions = List.copyOf(versions);
    }

    static DocumentHistoryResponse from(DocumentHistory history) {
        return new DocumentHistoryResponse(DocumentResponse.from(history.latest()), history.versions().stream()
                .map(v -> new Version(v.id(), v.version(), v.editedByUser(), v.createdAt()))
                .toList());
    }

    /** Una versión del historial, sin el contenido. */
    public record Version(UUID id, int version, boolean editedByUser, Instant createdAt) {
    }
}
