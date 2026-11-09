package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.analysis.domain.MatchScoreCalculator;
import com.jobpilot.analysis.domain.RequirementEvaluation;
import com.jobpilot.analysis.domain.ScoreResult;
import com.jobpilot.offer.domain.RequirementType;
import com.jobpilot.offer.domain.WorkMode;
import com.jobpilot.support.EnabledIfAnthropicApiKey;
import com.jobpilot.support.PostgresTestcontainer;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * JP-AI-01: validación con el LLM real (sección 2.6). Llama a Claude a través del {@link SpringAiLlmGateway} de la
 * aplicación, sin simular nada, y comprueba las respuestas con criterios flexibles (estructura y reglas), nunca
 * comparando texto.
 *
 * <p>Excluido de {@code mvnw verify} y de la CI ({@code excludedGroups} en pom.xml). Se ejecuta a mano con
 * {@code cd backend; .\mvnw.cmd verify -Pllm-live}, que lee la clave del entorno o de {@code ../.env}; sin clave, se
 * salta. Coste: 4 llamadas (3 extracciones + 1 encaje que reutiliza una de ellas), más un posible reintento por
 * respuesta no válida.
 */
@Tag("llm-live")
@EnabledIfAnthropicApiKey
@SpringBootTest(properties = "llm.mode=real")   // gana también a una variable de entorno LLM_MODE=fake
@ActiveProfiles("llm-live")                       // application-llm-live.yml: importa ../.env
@Import(PostgresTestcontainer.class)
class LlmLiveIT {

    private static final String FIXTURES = "/fixtures/llm-live/";
    private static final String MATCH_OFFER = "oferta-1-backend-java.txt";

    /** Una extracción por oferta en toda la clase: el test de encaje reutiliza la de {@link #MATCH_OFFER}. */
    private static final Map<String, OfferExtraction> EXTRACTIONS = new ConcurrentHashMap<>();

    @Autowired
    private LlmGateway gateway;

    @Autowired
    private Validator validator;

    @Test
    void elGatewayEsElAdaptadorRealDeSpringAi() {
        assertThat(gateway).isInstanceOf(SpringAiLlmGateway.class);
        assertThat(gateway.modelName()).startsWith("claude-");
    }

    @ParameterizedTest
    @ValueSource(strings = {MATCH_OFFER, "oferta-2-frontend-react.txt", "oferta-3-data-presencial.txt"})
    void laExtraccionCumpleElEsquemaYTieneAlMenosUnRequisitoObligatorio(String offer) {
        OfferExtraction extraction = extract(offer);

        assertThat(validator.validate(extraction)).as("Bean Validation de OfferExtraction").isEmpty();
        assertThat(new OfferExtractionValidator().validate(extraction)).as("OfferExtractionValidator").isEmpty();
        assertThat(extraction.title()).isNotBlank();
        assertThat(extraction.workMode()).isIn((Object[]) WorkMode.values());
        assertThat(extraction.requirements())
                .as("al menos un requisito obligatorio")
                .anyMatch(requirement -> requirement.type() == RequirementType.OBLIGATORIO);
    }

    @Test
    void elEncajeConElPerfilDeEjemploCumpleLasReglasYDaUnaPuntuacionEntre0Y100() {
        List<RequirementSnapshot> requirements = extract(MATCH_OFFER).requirements().stream()
                .map(requirement -> new RequirementSnapshot(UUID.randomUUID(), requirement.description(),
                        requirement.type(), requirement.category()))
                .toList();
        List<UUID> ids = requirements.stream().map(RequirementSnapshot::id).toList();

        MatchEvaluation evaluation = gateway.evaluateMatch(sampleProfile(), requirements);

        assertThat(validator.validate(evaluation)).as("Bean Validation de MatchEvaluation").isEmpty();
        assertThat(new MatchEvaluationValidator(ids).validate(evaluation)).as("MatchEvaluationValidator").isEmpty();
        assertThat(evaluation.matches())
                .as("cada requisito tiene exactamente un resultado")
                .allSatisfy(match -> assertThat(match.result()).isNotNull())
                .extracting(MatchEvaluation.Match::requirementId)
                .containsExactlyInAnyOrderElementsOf(ids);
        assertThat(evaluation.matches())
                .filteredOn(match -> match.result() == MatchResult.CUMPLE || match.result() == MatchResult.PARCIAL)
                .as("las evidencias de CUMPLE y PARCIAL no están vacías")
                .allSatisfy(match -> assertThat(match.evidence()).isNotBlank());
        assertThat(evaluation.suggestions()).hasSizeBetween(3, 5).allSatisfy(s -> assertThat(s).isNotBlank());
        assertThat(evaluation.summary()).isNotBlank();

        Map<UUID, RequirementType> types = requirements.stream()
                .collect(Collectors.toMap(RequirementSnapshot::id, RequirementSnapshot::type));
        ScoreResult score = new MatchScoreCalculator().calculate(evaluation.matches().stream()
                .map(match -> new RequirementEvaluation(types.get(match.requirementId()), match.result()))
                .toList());
        assertThat(score.score()).isBetween(0, 100);
    }

    private OfferExtraction extract(String offer) {
        return EXTRACTIONS.computeIfAbsent(offer, name -> gateway.extractOffer(fixture(name)));
    }

    private static ProfileSnapshot sampleProfile() {
        return JsonMapper.builder().build().readValue(fixture("perfil-ejemplo.json"), ProfileSnapshot.class);
    }

    private static String fixture(String name) {
        try (InputStream in = Objects.requireNonNull(LlmLiveIT.class.getResourceAsStream(FIXTURES + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se puede leer el fixture " + name, e);
        }
    }
}
