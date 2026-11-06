package com.jobpilot.ai;

/**
 * Puerto hacia el LLM (sección 2.1). Los servicios de dominio solo dependen de esta interfaz, nunca de Spring AI.
 *
 * <p>Cada ticket de funcionalidad añade aquí su método, con su plantilla en {@code prompts/*.st}, su validador y
 * su respuesta fija en {@code fake-llm/*.json}: {@code extractProfile} (JP-BE-04), {@code extractOffer} (JP-BE-05),
 * {@code evaluateMatch} (JP-BE-06) y {@code generateDocument} (JP-BE-07).
 *
 * <p>Errores que puede lanzar cualquier método: {@code LlmTimeoutException} (→ 504),
 * {@code LlmOverloadedException} (→ 503 + Retry-After) y {@code LlmInvalidResponseException} (→ 502).
 * Las llamadas tardan segundos: nunca se hacen dentro de una transacción de BD.
 */
public interface LlmGateway {

    /** Modelo que genera las respuestas; se guarda en {@code llm_model} para la trazabilidad. */
    String modelName();
}
