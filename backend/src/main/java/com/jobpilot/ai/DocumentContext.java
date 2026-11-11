package com.jobpilot.ai;

import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.document.domain.DocumentType;
import com.jobpilot.offer.domain.RequirementType;
import com.jobpilot.offer.domain.WorkMode;
import java.util.List;

/**
 * Lo que se envía al LLM para generar una carta o un CV adaptado (F4, JP-BE-07). Solo lo necesario para la tarea
 * (sección 2.5): la oferta, el perfil estructurado y el resultado del último análisis. Nunca el nombre ni el email
 * del usuario: la firma de la carta la añade el servidor después.
 *
 * @param type         carta o CV adaptado
 * @param offer        datos de la oferta y su texto original (para el idioma y el tono)
 * @param profile      perfil estructurado, sin el texto en bruto del CV
 * @param matchSummary resumen del último análisis de encaje
 * @param requirements resultado de cada requisito en el último análisis, en el orden de la oferta
 */
public record DocumentContext(
        DocumentType type,
        OfferData offer,
        ProfileSnapshot profile,
        String matchSummary,
        List<RequirementResult> requirements) {

    public DocumentContext {
        requirements = List.copyOf(requirements);
    }

    /** Datos de la oferta; {@code title}, {@code company} y {@code location} pueden ser null. */
    public record OfferData(String title, String company, String location, WorkMode workMode, String rawText) {
    }

    /** Un requisito con su resultado en el análisis; {@code evidence} null si no hay. */
    public record RequirementResult(String description, RequirementType type, MatchResult result, String evidence) {
    }
}
