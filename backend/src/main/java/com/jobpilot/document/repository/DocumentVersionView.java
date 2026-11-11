package com.jobpilot.document.repository;

import java.time.Instant;
import java.util.UUID;

/** Una versión de un documento sin su contenido, para el historial. */
public record DocumentVersionView(UUID id, short version, boolean editedByUser, Instant createdAt) {
}
