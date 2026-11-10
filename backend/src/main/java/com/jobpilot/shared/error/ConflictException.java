package com.jobpilot.shared.error;

import java.util.Objects;

/**
 * Conflicto con el estado actual del recurso (→ 409). Cada caso indica su propio tipo de problema
 * ({@code https://jobpilot.dev/problems/<typeSlug>}) y su título.
 */
public class ConflictException extends RuntimeException {

    /** Slug de {@link #profileConcurrentUpdate()}. */
    public static final String PROFILE_CONCURRENT_UPDATE = "profile-concurrent-update";

    private final String typeSlug;
    private final String title;

    public ConflictException(String typeSlug, String title, String detail) {
        super(detail);
        this.typeSlug = Objects.requireNonNull(typeSlug, "typeSlug");
        this.title = Objects.requireNonNull(title, "title");
    }

    /**
     * La {@code version} enviada no es la guardada: otra petición ha modificado el recurso desde que el cliente lo
     * leyó (bloqueo optimista).
     */
    public static ConflictException versionConflict() {
        return new ConflictException("version-conflict", "La candidatura ha cambiado",
                "La candidatura se ha modificado desde que la cargaste. Recarga el tablero e inténtalo de nuevo.");
    }

    /**
     * Dos {@code PUT /profile} a la vez: la otra petición ha creado o reemplazado el perfil mientras se guardaba
     * este, y la base de datos ha rechazado la escritura (un perfil por usuario, habilidades únicas).
     */
    public static ConflictException profileConcurrentUpdate() {
        return new ConflictException(PROFILE_CONCURRENT_UPDATE,
                "El perfil se ha guardado a la vez desde otra petición",
                "Tu perfil se ha guardado al mismo tiempo desde otra pestaña o petición. Recárgalo y vuelve a guardar "
                        + "los cambios.");
    }

    public String getTypeSlug() {
        return typeSlug;
    }

    public String getTitle() {
        return title;
    }
}
