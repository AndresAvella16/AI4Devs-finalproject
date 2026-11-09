package com.jobpilot.shared.error;

import java.util.Objects;

/**
 * Petición no válida por una regla que Bean Validation no puede comprobar porque depende del estado guardado
 * (p. ej. pedir el mismo estado que ya tiene la candidatura). Se responde igual que un fallo de Bean Validation:
 * 400 {@code validation-error} con {@code errors: [{field, message}]}.
 */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = Objects.requireNonNull(field, "field");
    }

    public String getField() {
        return field;
    }
}
