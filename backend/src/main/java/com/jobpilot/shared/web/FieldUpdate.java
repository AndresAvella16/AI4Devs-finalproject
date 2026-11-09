package com.jobpilot.shared.web;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Campo de un PATCH parcial que distingue "no enviado" de "enviado a null". En un record de petición:
 *
 * <ul>
 *   <li>campo ausente en el JSON → el componente es {@code null} (no se toca);</li>
 *   <li>{@code "campo": null} → {@link #isClear()} (se borra el valor);</li>
 *   <li>{@code "campo": valor} → {@link #value()}.</li>
 * </ul>
 *
 * <p>{@code Optional} no sirve para esto: con Jackson 3, un {@code Optional} ausente en un record llega como
 * {@code Optional.empty()}, igual que un {@code null} explícito. Las restricciones de Bean Validation sobre el tipo
 * contenido ({@code FieldUpdate<@Size(max = 5000) String>}) se aplican gracias a {@link FieldUpdateValueExtractor}.
 */
@JsonDeserialize(using = FieldUpdateDeserializer.class)
public record FieldUpdate<T>(T value) {

    private static final FieldUpdate<?> CLEAR = new FieldUpdate<>(null);

    public static <T> FieldUpdate<T> of(T value) {
        return new FieldUpdate<>(value);
    }

    @SuppressWarnings("unchecked")
    public static <T> FieldUpdate<T> clear() {
        return (FieldUpdate<T>) CLEAR;
    }

    public boolean isClear() {
        return value == null;
    }
}
