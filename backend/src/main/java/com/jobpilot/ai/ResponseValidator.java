package com.jobpilot.ai;

import java.util.List;

/**
 * Reglas de una respuesta del LLM que Bean Validation no puede expresar (p. ej. que cada {@code requirementId}
 * exista y no se repita). Devuelve los problemas encontrados, o una lista vacía si la respuesta es válida.
 *
 * <p>Los problemas se registran en el log: deben describir el fallo sin copiar el contenido de la respuesta, que
 * puede llevar datos personales del CV.
 */
@FunctionalInterface
interface ResponseValidator<T> {

    ResponseValidator<Object> NONE = response -> List.of();

    List<String> validate(T response);
}
