package com.jobpilot.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador real ({@code llm.mode=real}): Claude a través de Spring AI. Cada método construye un
 * {@link StructuredRequest} y lo ejecuta con {@link StructuredLlmCall}, que aplica el límite de concurrencia, la
 * validación con un reintento y la traducción de errores del proveedor.
 */
@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
public class SpringAiLlmGateway implements LlmGateway {

    static final String OFFER_EXTRACTION_PROMPT = "offer-extraction";
    static final String MATCH_PROMPT = "match";
    static final String CV_EXTRACTION_PROMPT = "cv-extraction";

    /**
     * Tokens de respuesta de la extracción del CV. Un CV de una página con un puesto ocupa ~1.600 tokens; uno de 3–4
     * páginas con 6–8 puestos se acerca a los 4.096 generales, y cortado por el límite el JSON no es válido (502).
     * Solo se paga lo que se genera: el límite más alto no encarece los CVs cortos. Sin {@code temperature}:
     * {@code claude-sonnet-5-5} la rechaza (400, "deprecated for this model"); la estabilidad entre llamadas sale de
     * las reglas de la plantilla (titular literal, habilidades separadas).
     */
    static final int CV_EXTRACTION_MAX_TOKENS = 8192;

    private static final OfferExtractionValidator OFFER_EXTRACTION_VALIDATOR = new OfferExtractionValidator();
    private static final ProfileExtractionValidator PROFILE_EXTRACTION_VALIDATOR = new ProfileExtractionValidator();

    /** Datos estructurados que van al modelo (perfil, requisitos) como JSON compacto y sin campos nulos. */
    private static final JsonMapper DATA_MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private final StructuredLlmCall call;

    private final String modelName;

    SpringAiLlmGateway(StructuredLlmCall call, @Value("${spring.ai.anthropic.chat.model}") String modelName) {
        this.call = call;
        this.modelName = modelName;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public ProfileExtraction extractProfile(String cvText) {
        return call.execute(StructuredRequest.builder(CV_EXTRACTION_PROMPT, ProfileExtraction.class)
                .data("cv", cvText)
                .validator(PROFILE_EXTRACTION_VALIDATOR)
                .maxTokens(CV_EXTRACTION_MAX_TOKENS)
                .build());
    }

    @Override
    public OfferExtraction extractOffer(String rawText) {
        return call.execute(StructuredRequest.builder(OFFER_EXTRACTION_PROMPT, OfferExtraction.class)
                .data("oferta", rawText)
                .validator(OFFER_EXTRACTION_VALIDATOR)
                .build());
    }

    @Override
    public MatchEvaluation evaluateMatch(ProfileSnapshot profile, List<RequirementSnapshot> requirements) {
        return call.execute(StructuredRequest.builder(MATCH_PROMPT, MatchEvaluation.class)
                .data("perfil", DATA_MAPPER.writeValueAsString(profile))
                .data("requisitos", DATA_MAPPER.writeValueAsString(requirements))
                .validator(new MatchEvaluationValidator(requirements.stream().map(RequirementSnapshot::id).toList()))
                .build());
    }
}
