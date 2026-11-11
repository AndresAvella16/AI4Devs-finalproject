package com.jobpilot.ai;

import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.document.domain.DocumentType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LLM simulado ({@code llm.mode=fake}, el modo por defecto): devuelve respuestas fijas de
 * {@code src/main/resources/fake-llm/*.json}. Se usa en desarrollo sin API key, en los tests de integración y en
 * los E2E. No aplica el límite de concurrencia: no tiene coste ni latencia.
 */
@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "fake", matchIfMissing = true)
public class FakeLlmGateway implements LlmGateway {

    static final String MODEL_NAME = "fake";

    private final FakeResponses responses;

    FakeLlmGateway(FakeResponses responses) {
        this.responses = responses;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    /** Siempre el perfil del usuario del seed de E2E, sea cual sea el CV: con la oferta fake da el 85 del readme. */
    @Override
    public ProfileExtraction extractProfile(String cvText) {
        return responses.load("cv-extraction", ProfileExtraction.class);
    }

    /** Siempre la oferta del Ejemplo 1 del readme (3 obligatorios y 3 deseables), sea cual sea el texto. */
    @Override
    public OfferExtraction extractOffer(String rawText) {
        return responses.load("offer-extraction", OfferExtraction.class);
    }

    /**
     * Los ids de los requisitos se generan en cada ejecución, así que el JSON lleva los resultados por posición: el
     * requisito {@code i} recibe el resultado {@code i} (la secuencia se repite si hay más requisitos). Con la oferta
     * fake da los resultados del Ejemplo 2 del readme (→ 85).
     */
    @Override
    public MatchEvaluation evaluateMatch(ProfileSnapshot profile, List<RequirementSnapshot> requirements) {
        FakeMatchEvaluation template = responses.load("match-evaluation", FakeMatchEvaluation.class);
        List<MatchEvaluation.Match> matches = new ArrayList<>();
        for (int i = 0; i < requirements.size(); i++) {
            FakeMatchEvaluation.Result result = template.results().get(i % template.results().size());
            matches.add(new MatchEvaluation.Match(requirements.get(i).id(), result.result(), result.evidence()));
        }
        return new MatchEvaluation(matches, template.summary(), template.suggestions());
    }

    /** Forma de {@code fake-llm/match-evaluation.json}: como {@link MatchEvaluation}, pero sin ids. */
    record FakeMatchEvaluation(List<Result> results, String summary, List<String> suggestions) {

        record Result(MatchResult result, String evidence) {
        }
    }

    /**
     * Siempre la misma carta ({@code document-carta.json}) o el mismo CV adaptado ({@code document-cv-adaptado.json}),
     * escritos para la oferta y el perfil fake. La carta acaba en "Atentamente," sin firma, como pide el prompt real.
     */
    @Override
    public GeneratedDocumentContent generateDocument(DocumentContext context) {
        String name = context.type() == DocumentType.CARTA ? "document-carta" : "document-cv-adaptado";
        return responses.load(name, GeneratedDocumentContent.class);
    }
}
