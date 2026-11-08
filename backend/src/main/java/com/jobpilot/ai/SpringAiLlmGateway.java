package com.jobpilot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador real ({@code llm.mode=real}): Claude a través de Spring AI. Cada método construye un
 * {@link StructuredRequest} y lo ejecuta con {@link StructuredLlmCall}, que aplica el límite de concurrencia, la
 * validación con un reintento y la traducción de errores del proveedor.
 */
@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
public class SpringAiLlmGateway implements LlmGateway {

    static final String OFFER_EXTRACTION_PROMPT = "offer-extraction";

    private static final OfferExtractionValidator OFFER_EXTRACTION_VALIDATOR = new OfferExtractionValidator();

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
    public OfferExtraction extractOffer(String rawText) {
        return call.execute(StructuredRequest.builder(OFFER_EXTRACTION_PROMPT, OfferExtraction.class)
                .data("oferta", rawText)
                .validator(OFFER_EXTRACTION_VALIDATOR)
                .build());
    }
}
