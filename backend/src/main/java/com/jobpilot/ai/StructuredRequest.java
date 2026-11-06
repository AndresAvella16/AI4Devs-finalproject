package com.jobpilot.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Una llamada estructurada al LLM.
 *
 * @param promptName   plantilla {@code prompts/<promptName>.st} con las instrucciones de la tarea
 * @param variables    variables de la plantilla, puestas por el código (nunca texto del usuario)
 * @param data         bloques de datos no confiables (CV, oferta...), en orden: nombre del bloque → contenido
 * @param responseType {@code record} al que se convierte la respuesta (con anotaciones de Bean Validation)
 * @param validator    reglas adicionales de la respuesta
 */
record StructuredRequest<T>(
        String promptName,
        Map<String, Object> variables,
        Map<String, String> data,
        Class<T> responseType,
        ResponseValidator<? super T> validator) {

    private static final Pattern SECTION_NAME = Pattern.compile("[a-z][a-z0-9_]*");

    StructuredRequest {
        Objects.requireNonNull(promptName, "promptName");
        Objects.requireNonNull(responseType, "responseType");
        Objects.requireNonNull(validator, "validator");
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    static <T> Builder<T> builder(String promptName, Class<T> responseType) {
        return new Builder<>(promptName, responseType);
    }

    static final class Builder<T> {

        private final String promptName;
        private final Class<T> responseType;
        private final Map<String, Object> variables = new LinkedHashMap<>();
        private final Map<String, String> data = new LinkedHashMap<>();
        private ResponseValidator<? super T> validator = ResponseValidator.NONE;

        private Builder(String promptName, Class<T> responseType) {
            this.promptName = promptName;
            this.responseType = responseType;
        }

        Builder<T> variable(String name, Object value) {
            variables.put(name, Objects.requireNonNull(value, name));
            return this;
        }

        /** Añade un bloque de datos no confiables; se envía como {@code <section>...</section>}. */
        Builder<T> data(String section, String content) {
            if (!SECTION_NAME.matcher(section).matches()) {
                throw new IllegalArgumentException("Nombre de bloque no válido: " + section);
            }
            data.put(section, Objects.requireNonNull(content, section));
            return this;
        }

        Builder<T> validator(ResponseValidator<? super T> validator) {
            this.validator = validator;
            return this;
        }

        StructuredRequest<T> build() {
            return new StructuredRequest<>(promptName, variables, data, responseType, validator);
        }
    }
}
