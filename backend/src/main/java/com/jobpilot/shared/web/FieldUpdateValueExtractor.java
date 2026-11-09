package com.jobpilot.shared.web;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

/**
 * Permite validar el valor de un {@link FieldUpdate} ({@code FieldUpdate<@Size(max = 5000) String>}). Se registra
 * con {@code META-INF/services/jakarta.validation.valueextraction.ValueExtractor}. Un {@code clear()} extrae
 * {@code null}, que las restricciones como {@code @Size} dan por válido.
 */
public class FieldUpdateValueExtractor implements ValueExtractor<FieldUpdate<@ExtractedValue ?>> {

    @Override
    public void extractValues(FieldUpdate<?> originalValue, ValueReceiver receiver) {
        receiver.value(null, originalValue.value());
    }
}
