package com.jobpilot.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.ai.LlmGateway;
import com.jobpilot.ai.MatchEvaluation;
import com.jobpilot.ai.RequirementSnapshot;
import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.auth.service.TokenService;
import com.jobpilot.shared.error.LlmInvalidResponseException;
import com.jobpilot.shared.error.LlmOverloadedException;
import com.jobpilot.shared.error.LlmTimeoutException;
import com.jobpilot.support.PostgresTestcontainer;
import com.jobpilot.support.TestRows;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * JP-BE-06 de punta a punta (API → servicio → BD) con {@code FakeLlmGateway}. La oferta se importa con
 * {@code POST /offers} (la oferta fake del Ejemplo 1: 3 obligatorios y 3 deseables) y el fake del encaje devuelve
 * los resultados del Ejemplo 2 (→ 85). Los fallos del LLM se simulan espiando ese mismo bean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class AnalysisControllerIT {

    private static final String OFFERS = "/api/v1/offers";
    private static final String PROBLEMS = "https://jobpilot.dev/problems/";
    private static final String RAW_TEXT = "Buscamos Desarrollador/a Java Junior en Valencia. ".repeat(6);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    @MockitoSpyBean
    private LlmGateway llm;

    private TestRows rows;
    private UUID userId;
    private String token;

    @BeforeEach
    void userWithProfile() {
        rows = new TestRows(jdbc);
        userId = rows.user();
        token = tokenFor(userId);
        UUID profile = rows.profile(userId);
        rows.skill(profile, "Java");
        rows.experience(profile);
    }

    private String tokenFor(UUID user) {
        return "Bearer " + tokens.issue(user).value();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String authorization) throws Exception {
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private ResultActions analyze(UUID offerId, String authorization) throws Exception {
        return perform(post(analysisPath(offerId)), authorization);
    }

    private ResultActions latest(UUID offerId, String authorization) throws Exception {
        return perform(get(analysisPath(offerId)), authorization);
    }

    private static String analysisPath(UUID offerId) {
        return OFFERS + "/" + offerId + "/analysis";
    }

    private static JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Importa la oferta fake del Ejemplo 1 para el usuario del token dado. */
    private UUID importedOffer(String authorization) throws Exception {
        ResultActions created = perform(post(OFFERS).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("rawText", RAW_TEXT))), authorization)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extractionStatus").value("COMPLETADA"));
        return UUID.fromString(json(created).get("id").asString());
    }

    private UUID offerInStatus(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO job_offers (id, user_id, raw_text, extraction_status) VALUES (?, ?, ?, ?)", id,
                userId, RAW_TEXT, status);
        return id;
    }

    private static ResultActions expectProblem(ResultActions result, int status, String slug) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + slug));
    }

    private int analysesInDb() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM match_analyses a JOIN job_offers o ON o.id = a.offer_id
                WHERE o.user_id = ?""", Integer.class, userId);
    }

    private int requirementMatchesInDb() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM requirement_matches m JOIN job_offers o ON o.id = m.offer_id
                WHERE o.user_id = ?""", Integer.class, userId);
    }

    // ---- CA 1: análisis con la puntuación esperada exacta ----

    @Test
    void analizarLaOfertaDelEjemplo1Devuelve201ConPuntuacion85YLaGuardaEnBd() throws Exception {
        UUID offerId = importedOffer(token);

        ResultActions result = analyze(offerId, token)
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.offerId").value(offerId.toString()))
                .andExpect(jsonPath("$.score").value(85))
                .andExpect(jsonPath("$.hasBlockingGap").value(false))
                .andExpect(jsonPath("$.summary").isString())
                .andExpect(jsonPath("$.suggestions", hasSize(3)))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.matches", hasSize(6)))
                .andExpect(jsonPath("$.matches[0].description")
                        .value("Al menos 1 año de experiencia con Java y Spring Boot"))
                .andExpect(jsonPath("$.matches[0].type").value("OBLIGATORIO"))
                .andExpect(jsonPath("$.matches[0].result").value("CUMPLE"))
                .andExpect(jsonPath("$.matches[0].evidence").isString())
                .andExpect(jsonPath("$.matches[3].description").value("Experiencia con React"))
                .andExpect(jsonPath("$.matches[3].type").value("DESEABLE"))
                .andExpect(jsonPath("$.matches[3].result").value("PARCIAL"))
                .andExpect(jsonPath("$.matches[4].description").value("Docker"))
                .andExpect(jsonPath("$.matches[4].result").value("NO_CUMPLE"))
                .andExpect(jsonPath("$.matches[4].evidence").value(nullValue()))
                .andExpect(jsonPath("$.matches[5].result").value("CUMPLE"));

        JsonNode body = json(result);
        UUID analysisId = UUID.fromString(body.get("id").asString());
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT offer_id, profile_id, score, has_blocking_gap, summary, jsonb_array_length(suggestions) AS n,
                       llm_model
                FROM match_analyses WHERE id = ?""", analysisId);
        assertThat(row.get("offer_id")).isEqualTo(offerId);
        assertThat(row.get("profile_id")).isEqualTo(
                jdbc.queryForObject("SELECT id FROM profiles WHERE user_id = ?", UUID.class, userId));
        assertThat(((Number) row.get("score")).intValue()).isEqualTo(85);
        assertThat(row.get("has_blocking_gap")).isEqualTo(false);
        assertThat(row.get("summary")).isEqualTo(body.get("summary").asString());
        assertThat(((Number) row.get("n")).intValue()).isEqualTo(3);
        assertThat(row.get("llm_model")).isEqualTo("fake");

        List<String> requirementIds = jdbc.queryForList(
                "SELECT id::text FROM offer_requirements WHERE offer_id = ? ORDER BY sort_order", String.class,
                offerId);
        List<String> matchedIds = new ArrayList<>();
        body.get("matches").forEach(match -> matchedIds.add(match.get("requirementId").asString()));
        assertThat(matchedIds).containsExactlyElementsOf(requirementIds);
        assertThat(jdbc.queryForList("SELECT result FROM requirement_matches WHERE analysis_id = ?", String.class,
                analysisId)).hasSize(6);
    }

    // ---- CA 2: requisito bloqueante ----

    @Test
    void unObligatorioSinCumplirMarcaElAnalisisComoBloqueante() throws Exception {
        UUID offerId = importedOffer(token);
        doAnswer(invocation -> {
            List<RequirementSnapshot> requirements = invocation.getArgument(1);
            List<MatchEvaluation.Match> matches = new ArrayList<>();
            for (RequirementSnapshot requirement : requirements) {
                MatchResult result = matches.isEmpty() ? MatchResult.NO_CUMPLE : MatchResult.CUMPLE;
                matches.add(new MatchEvaluation.Match(requirement.id(), result, null));
            }
            return new MatchEvaluation(matches, "Te falta un requisito obligatorio.", List.of("Uno", "Dos", "Tres"));
        }).when(llm).evaluateMatch(any(), anyList());

        // obligatorios 2/3, deseables 1 → 46,67 + 30 = 76,67 → 77
        analyze(offerId, token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasBlockingGap").value(true))
                .andExpect(jsonPath("$.score").value(77))
                .andExpect(jsonPath("$.matches[0].type").value("OBLIGATORIO"))
                .andExpect(jsonPath("$.matches[0].result").value("NO_CUMPLE"));
    }

    // ---- CA 3: propiedad de los datos ----

    @Test
    void unUsuarioNoPuedeAnalizarNiVerElAnalisisDeLaOfertaDeOtro() throws Exception {
        UUID offerId = importedOffer(token);
        analyze(offerId, token).andExpect(status().isCreated());
        UUID other = rows.user();
        rows.profile(other);
        String otherToken = tokenFor(other);

        expectProblem(analyze(offerId, otherToken), 404, "not-found");
        expectProblem(latest(offerId, otherToken), 404, "not-found");
        assertThat(analysesInDb()).isEqualTo(1);
    }

    @Test
    void unaOfertaQueNoExisteEs404() throws Exception {
        expectProblem(analyze(UUID.randomUUID(), token), 404, "not-found");
        expectProblem(latest(UUID.randomUUID(), token), 404, "not-found");
    }

    // ---- CA 4 y 5: 409 ----

    @Test
    void sinPerfilEs409ProfileRequiredYNoSeLlamaAlLlm() throws Exception {
        UUID withoutProfile = rows.user();
        String withoutProfileToken = tokenFor(withoutProfile);
        UUID offerId = importedOffer(withoutProfileToken);

        expectProblem(analyze(offerId, withoutProfileToken), 409, "profile-required")
                .andExpect(jsonPath("$.title").value("Necesitas crear tu perfil"))
                .andExpect(jsonPath("$.instance").value(analysisPath(offerId)));
        verify(llm, never()).evaluateMatch(any(), anyList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDIENTE", "FALLIDA"})
    void unaOfertaSinRequisitosExtraidosEs409(String status) throws Exception {
        UUID offerId = offerInStatus(status);

        expectProblem(analyze(offerId, token), 409, "offer-not-extracted")
                .andExpect(jsonPath("$.title").value("La oferta no tiene requisitos extraídos"));
        verify(llm, never()).evaluateMatch(any(), anyList());
    }

    // ---- CA 6 y 7: errores de la IA, sin guardar nada ----

    static Stream<Arguments> llmErrorsAndStatus() {
        return Stream.of(
                Arguments.of(new LlmInvalidResponseException("no válida dos veces"), 502, "llm-invalid-response"),
                Arguments.of(new LlmTimeoutException("timeout"), 504, "llm-timeout"),
                Arguments.of(new LlmOverloadedException(10), 503, "llm-overloaded"));
    }

    @ParameterizedTest
    @MethodSource("llmErrorsAndStatus")
    void siLaIaFallaSeDevuelveSuErrorYNoSeGuardaNada(RuntimeException error, int status, String slug)
            throws Exception {
        UUID offerId = importedOffer(token);
        doThrow(error).when(llm).evaluateMatch(any(), anyList());

        expectProblem(analyze(offerId, token), status, slug);

        assertThat(analysesInDb()).isZero();
        assertThat(requirementMatchesInDb()).isZero();
    }

    @Test
    void concurrenciaAgotadaEs503ConRetryAfter() throws Exception {
        UUID offerId = importedOffer(token);
        doThrow(new LlmOverloadedException(10)).when(llm).evaluateMatch(any(), anyList());

        expectProblem(analyze(offerId, token), 503, "llm-overloaded")
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "10"));
    }

    // ---- CA 8: último análisis ----

    @Test
    void getDevuelveElAnalisisMasReciente() throws Exception {
        UUID offerId = importedOffer(token);
        analyze(offerId, token).andExpect(status().isCreated());
        String newest = json(analyze(offerId, token).andExpect(status().isCreated())).get("id").asString();

        latest(offerId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newest))
                .andExpect(jsonPath("$.offerId").value(offerId.toString()))
                .andExpect(jsonPath("$.score").value(85))
                .andExpect(jsonPath("$.matches", hasSize(6)))
                .andExpect(jsonPath("$.matches[0].description")
                        .value("Al menos 1 año de experiencia con Java y Spring Boot"))
                .andExpect(jsonPath("$.matches[4].result").value("NO_CUMPLE"))
                .andExpect(jsonPath("$.suggestions", hasSize(3)));
        assertThat(analysesInDb()).isEqualTo(2);
    }

    @Test
    void getDeUnaOfertaSinAnalisisEs404() throws Exception {
        expectProblem(latest(importedOffer(token), token), 404, "not-found");
    }

    @Test
    void elAnalisisApareceEnElDashboard() throws Exception {
        UUID offerId = importedOffer(token);
        analyze(offerId, token).andExpect(status().isCreated());

        perform(get(OFFERS), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(offerId.toString()))
                .andExpect(jsonPath("$[0].latestScore").value(85))
                .andExpect(jsonPath("$[0].hasBlockingGap").value(false));
    }

    // ---- CA 9: autenticación ----

    @Test
    void sinTokenEs401() throws Exception {
        UUID offerId = UUID.randomUUID();
        expectProblem(analyze(offerId, null), 401, "unauthorized");
        expectProblem(latest(offerId, null), 401, "unauthorized");
    }

    // ---- CA 11: documentación ----

    @Test
    void losEndpointsAparecenEnOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/offers/{offerId}/analysis'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/offers/{offerId}/analysis'].post.tags[0]").value("Analysis"))
                .andExpect(jsonPath("$.paths['/api/v1/offers/{offerId}/analysis'].get").exists());
    }
}
