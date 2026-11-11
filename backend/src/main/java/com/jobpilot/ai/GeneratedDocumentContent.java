package com.jobpilot.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Documento devuelto por el LLM ({@link LlmGateway#generateDocument}): el texto en Markdown. Como mucho 20.000
 * caracteres, el mismo máximo que una edición del usuario. Las reglas por tipo (palabras de la carta) están en
 * {@link GeneratedDocumentValidator}.
 *
 * @param contentMarkdown carta o CV adaptado, en Markdown
 */
public record GeneratedDocumentContent(@NotBlank @Size(max = 20_000) String contentMarkdown) {
}
