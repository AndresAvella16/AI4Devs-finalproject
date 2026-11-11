package com.jobpilot.document.service;

/**
 * Un documento exportado a PDF.
 *
 * @param fileName nombre del fichero de la descarga, p. ej. {@code carta-v3.pdf}
 * @param content  bytes del PDF
 */
public record DocumentPdf(String fileName, byte[] content) {
}
