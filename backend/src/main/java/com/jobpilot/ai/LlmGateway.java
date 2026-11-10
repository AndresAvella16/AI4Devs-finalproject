package com.jobpilot.ai;

import java.util.List;

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

    /**
     * Estructura el texto de un CV en un borrador de perfil (F1, JP-BE-04). El texto va al modelo como datos no
     * confiables; la respuesta cumple {@link ProfileExtraction} y {@link ProfileExtractionValidator}. El borrador
     * no se guarda: el usuario lo revisa y lo confirma con {@code PUT /profile}.
     */
    ProfileExtraction extractProfile(String cvText);

    /**
     * Extrae los datos clave y los requisitos de una oferta (F2, JP-BE-05). El texto va al modelo como datos no
     * confiables; la respuesta cumple {@link OfferExtraction} y {@link OfferExtractionValidator}.
     */
    OfferExtraction extractOffer(String rawText);

    /**
     * Clasifica cada requisito de la oferta frente al perfil (CUMPLE / PARCIAL / NO_CUMPLE) con su evidencia, y
     * propone un resumen y 3–5 sugerencias (F3, JP-BE-06). El perfil y los requisitos van al modelo como datos no
     * confiables; la respuesta cumple {@link MatchEvaluation} y {@link MatchEvaluationValidator}: cada requisito
     * aparece exactamente una vez. La puntuación no la da el modelo: la calcula {@code MatchScoreCalculator}.
     */
    MatchEvaluation evaluateMatch(ProfileSnapshot profile, List<RequirementSnapshot> requirements);
}
