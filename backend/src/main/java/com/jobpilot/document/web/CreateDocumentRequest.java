package com.jobpilot.document.web;

import com.jobpilot.document.domain.DocumentType;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /offers/{offerId}/documents}: qué documento generar. */
public record CreateDocumentRequest(@NotNull DocumentType type) {
}
