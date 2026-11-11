package com.jobpilot.document.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /documents/{documentId}/versions}: el texto editado por el usuario. {@code @NotBlank} y no
 * solo longitud mínima 1: un texto que solo tiene espacios tampoco es un documento.
 */
public record CreateDocumentVersionRequest(@NotBlank @Size(max = 20_000) String contentMarkdown) {
}
