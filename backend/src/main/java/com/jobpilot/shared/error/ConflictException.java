package com.jobpilot.shared.error;

import java.util.Objects;

/**
 * Conflicto con el estado actual del recurso (→ 409). Cada caso indica su propio tipo de problema
 * ({@code https://jobpilot.dev/problems/<typeSlug>}) y su título.
 */
public class ConflictException extends RuntimeException {

    private final String typeSlug;
    private final String title;

    public ConflictException(String typeSlug, String title, String detail) {
        super(detail);
        this.typeSlug = Objects.requireNonNull(typeSlug, "typeSlug");
        this.title = Objects.requireNonNull(title, "title");
    }

    public String getTypeSlug() {
        return typeSlug;
    }

    public String getTitle() {
        return title;
    }
}
