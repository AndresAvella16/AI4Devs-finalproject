package com.jobpilot.document.web;

import com.jobpilot.document.domain.DocumentType;
import com.jobpilot.document.domain.GeneratedDocument;
import java.time.Instant;
import java.util.UUID;

/** Una versión completa de una carta o de un CV adaptado. */
public record DocumentResponse(
        UUID id,
        DocumentType type,
        int version,
        String contentMarkdown,
        boolean editedByUser,
        Instant createdAt) {

    static DocumentResponse from(GeneratedDocument document) {
        return new DocumentResponse(document.getId(), document.getType(), document.getVersion(),
                document.getContentMarkdown(), document.isEditedByUser(), document.getCreatedAt());
    }
}
