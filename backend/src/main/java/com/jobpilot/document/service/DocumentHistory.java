package com.jobpilot.document.service;

import com.jobpilot.document.domain.GeneratedDocument;
import com.jobpilot.document.repository.DocumentVersionView;
import java.util.List;

/**
 * La última versión completa de un documento y todas sus versiones sin contenido, de la más reciente a la más
 * antigua.
 */
public record DocumentHistory(GeneratedDocument latest, List<DocumentVersionView> versions) {

    public DocumentHistory {
        versions = List.copyOf(versions);
    }
}
