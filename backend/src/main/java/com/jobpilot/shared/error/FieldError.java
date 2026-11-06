package com.jobpilot.shared.error;

/** Elemento de la propiedad {@code errors} de un ProblemDetail de validación. */
public record FieldError(String field, String message) {
}
