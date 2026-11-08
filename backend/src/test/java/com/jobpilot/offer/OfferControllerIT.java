package com.jobpilot.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.ai.LlmGateway;
import com.jobpilot.auth.service.TokenService;
import com.jobpilot.shared.error.LlmInvalidResponseException;
import com.jobpilot.shared.error.LlmOverloadedException;
import com.jobpilot.shared.error.LlmTimeoutException;
import com.jobpilot.support.PostgresTestcontainer;
import com.jobpilot.support.TestRows;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * JP-BE-05 de punta a punta (API → servicio → BD) con {@code FakeLlmGateway}, que devuelve la oferta del Ejemplo 1
 * del readme. Los fallos del LLM se simulan espiando ese mismo bean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class OfferControllerIT {

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
    void newUser() {
        rows = new TestRows(jdbc);
        userId = rows.user();
        token = tokenFor(userId);
    }

    private String tokenFor(UUID user) {
        return "Bearer " + tokens.issue(user).value();
    }

    private static String body(String rawText, String sourceUrl) {
        return JSON.writeValueAsString(sourceUrl == null
                ? Map.of("rawText", rawText)
                : Map.of("rawText", rawText, "sourceUrl", sourceUrl));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String authorization) throws Exception {
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private ResultActions createOffer(String json) throws Exception {
        return perform(post(OFFERS).contentType(MediaType.APPLICATION_JSON).content(json), token);
    }

    private ResultActions retry(UUID offerId, String authorization) throws Exception {
        return perform(post(OFFERS + "/" + offerId + "/extraction"), authorization);
    }

    private UUID createdId(ResultActions result) throws Exception {
        MvcResult mvc = result.andReturn();
        return UUID.fromString(JSON.readTree(mvc.getResponse().getContentAsString()).get("id").asString());
    }

    private UUID failedOffer() throws Exception {
        doThrow(new LlmTimeoutException("timeout")).when(llm).extractOffer(anyString());
        UUID id = createdId(createOffer(body(RAW_TEXT, null)).andExpect(status().isCreated()));
        org.mockito.Mockito.reset(llm);
        return id;
    }

    private static ResultActions expectProblem(ResultActions result, int status, String slug) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + slug));
    }

    private String statusInDb(UUID offerId) {
        return jdbc.queryForObject("SELECT extraction_status FROM job_offers WHERE id = ?", String.class, offerId);
    }

    private int requirementsInDb(UUID offerId) {
        return jdbc.queryForObject("SELECT count(*) FROM offer_requirements WHERE offer_id = ?", Integer.class,
                offerId);
    }

    // ---- CA 1: importación correcta ----

    @Test
    void importarUnaOfertaDevuelve201ConLosDatosYLosRequisitosExtraidos() throws Exception {
        ResultActions result = createOffer(body(RAW_TEXT, "https://empresa-ejemplo.com/empleo/java-junior"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Desarrollador/a Java Junior"))
                .andExpect(jsonPath("$.company").value(nullValue()))
                .andExpect(jsonPath("$.location").value("Valencia"))
                .andExpect(jsonPath("$.workMode").value("HIBRIDO"))
                .andExpect(jsonPath("$.salary.min").value(24000))
                .andExpect(jsonPath("$.salary.max").value(28000))
                .andExpect(jsonPath("$.salary.currency").value("EUR"))
                .andExpect(jsonPath("$.sourceUrl").value("https://empresa-ejemplo.com/empleo/java-junior"))
                .andExpect(jsonPath("$.extractionStatus").value("COMPLETADA"))
                .andExpect(jsonPath("$.requirements", hasSize(6)))
                .andExpect(jsonPath("$.requirements[0].description")
                        .value("Al menos 1 año de experiencia con Java y Spring Boot"))
                .andExpect(jsonPath("$.requirements[0].type").value("OBLIGATORIO"))
                .andExpect(jsonPath("$.requirements[0].category").value("EXPERIENCIA"))
                .andExpect(jsonPath("$.requirements[5].description").value("Inglés B2"))
                .andExpect(jsonPath("$.requirements[5].type").value("DESEABLE"))
                .andExpect(jsonPath("$.requirements[0].id").isString())
                .andExpect(jsonPath("$.createdAt").isString());

        UUID id = createdId(result);
        result.andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/offers/" + id));
        assertThat(statusInDb(id)).isEqualTo("COMPLETADA");
        assertThat(requirementsInDb(id)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT raw_text FROM job_offers WHERE id = ?", String.class, id))
                .isEqualTo(RAW_TEXT);
        assertThat(jdbc.queryForObject("SELECT user_id FROM job_offers WHERE id = ?", UUID.class, id))
                .isEqualTo(userId);
    }

    @Test
    void laOfertaImportadaApareceEnElDashboardYEnSuDetalle() throws Exception {
        UUID id = createdId(createOffer(body(RAW_TEXT, null)).andExpect(status().isCreated()));

        perform(get(OFFERS), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].title").value("Desarrollador/a Java Junior"))
                .andExpect(jsonPath("$[0].extractionStatus").value("COMPLETADA"))
                .andExpect(jsonPath("$[0].latestScore").value(nullValue()))
                .andExpect(jsonPath("$[0].hasBlockingGap").value(nullValue()))
                .andExpect(jsonPath("$[0].applicationStatus").value(nullValue()));

        perform(get(OFFERS + "/" + id), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.sourceUrl").value(nullValue()))
                .andExpect(jsonPath("$.requirements", hasSize(6)));
    }

    // ---- CA 2: fallo de la IA → 201 FALLIDA ----

    static Stream<RuntimeException> llmErrors() {
        return Stream.of(new LlmTimeoutException("timeout"), new LlmOverloadedException(10),
                new LlmInvalidResponseException("no válida"));
    }

    @ParameterizedTest
    @MethodSource("llmErrors")
    void siLaIaFallaLaOfertaSeGuardaFallidaConSuTextoYSeResponde201(RuntimeException error) throws Exception {
        doThrow(error).when(llm).extractOffer(anyString());

        ResultActions result = createOffer(body(RAW_TEXT, "https://acme.dev/oferta"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extractionStatus").value("FALLIDA"))
                .andExpect(jsonPath("$.title").value(nullValue()))
                .andExpect(jsonPath("$.workMode").value("DESCONOCIDO"))
                .andExpect(jsonPath("$.salary").value(nullValue()))
                .andExpect(jsonPath("$.sourceUrl").value("https://acme.dev/oferta"))
                .andExpect(jsonPath("$.requirements", hasSize(0)));

        UUID id = createdId(result);
        assertThat(statusInDb(id)).isEqualTo("FALLIDA");
        assertThat(jdbc.queryForObject("SELECT raw_text FROM job_offers WHERE id = ?", String.class, id))
                .isEqualTo(RAW_TEXT);
    }

    @Test
    void unErrorQueNoEsDeIaDa500PeroElTextoQuedaGuardadoEnPendiente() throws Exception {
        doThrow(new IllegalStateException("fallo inesperado")).when(llm).extractOffer(anyString());

        expectProblem(createOffer(body(RAW_TEXT, null)), 500, "internal-error");

        assertThat(jdbc.queryForList("SELECT extraction_status FROM job_offers WHERE user_id = ?", String.class,
                userId)).containsExactly("PENDIENTE");
    }

    // ---- CA 3–5: reintentar la extracción ----

    @Test
    void reintentarUnaOfertaFallidaDevuelve200ConLosRequisitos() throws Exception {
        UUID id = failedOffer();

        retry(id, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.extractionStatus").value("COMPLETADA"))
                .andExpect(jsonPath("$.title").value("Desarrollador/a Java Junior"))
                .andExpect(jsonPath("$.requirements", hasSize(6)));

        assertThat(statusInDb(id)).isEqualTo("COMPLETADA");
        assertThat(requirementsInDb(id)).isEqualTo(6);
    }

    @Test
    void reintentarUnaOfertaPendienteTambienSePuede() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO job_offers (id, user_id, raw_text, extraction_status) VALUES (?, ?, ?, 'PENDIENTE')",
                id, userId, RAW_TEXT);

        retry(id, token).andExpect(status().isOk()).andExpect(jsonPath("$.extractionStatus").value("COMPLETADA"));
    }

    @Test
    void elReintentoBorraLosRequisitosYAnalisisAnterioresEnLaMismaOperacion() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO job_offers (id, user_id, raw_text, extraction_status) VALUES (?, ?, ?, 'FALLIDA')",
                id, userId, RAW_TEXT);
        UUID oldRequirement = rows.requirement(id);
        UUID analysis = rows.analysis(id, rows.profile(userId));
        rows.requirementMatch(analysis, oldRequirement, id);

        retry(id, token).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM offer_requirements WHERE id = ?", Integer.class,
                oldRequirement)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_analyses WHERE offer_id = ?", Integer.class, id))
                .isZero();
        assertThat(requirementsInDb(id)).isEqualTo(6);
    }

    @Test
    void reintentarUnaOfertaCompletadaEs409() throws Exception {
        UUID id = createdId(createOffer(body(RAW_TEXT, null)));

        expectProblem(retry(id, token), 409, "offer-already-extracted")
                .andExpect(jsonPath("$.title").value("La oferta ya tiene sus requisitos extraídos"))
                .andExpect(jsonPath("$.instance").value(OFFERS + "/" + id + "/extraction"));
        assertThat(requirementsInDb(id)).isEqualTo(6);
    }

    static Stream<Arguments> llmErrorsAndStatus() {
        return Stream.of(
                Arguments.of(new LlmTimeoutException("timeout"), 504, "llm-timeout"),
                Arguments.of(new LlmOverloadedException(10), 503, "llm-overloaded"),
                Arguments.of(new LlmInvalidResponseException("no válida"), 502, "llm-invalid-response"));
    }

    @ParameterizedTest
    @MethodSource("llmErrorsAndStatus")
    void siElReintentoFallaSeDevuelveElErrorDeIaYLaOfertaSigueFallida(RuntimeException error, int status,
            String slug) throws Exception {
        UUID id = failedOffer();
        doThrow(error).when(llm).extractOffer(anyString());

        expectProblem(retry(id, token), status, slug);

        assertThat(statusInDb(id)).isEqualTo("FALLIDA");
        assertThat(requirementsInDb(id)).isZero();
    }

    @Test
    void siElReintentoDeUnaPendienteFallaPasaAFallida() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO job_offers (id, user_id, raw_text, extraction_status) VALUES (?, ?, ?, 'PENDIENTE')",
                id, userId, RAW_TEXT);
        doThrow(new LlmOverloadedException(10)).when(llm).extractOffer(anyString());

        expectProblem(retry(id, token), 503, "llm-overloaded").andExpect(header().string("Retry-After", "10"));

        assertThat(statusInDb(id)).isEqualTo("FALLIDA");
    }

    // ---- CA 6: validación ----

    @ParameterizedTest
    @ValueSource(ints = {0, 199, 20_001})
    void unTextoFueraDeLosLimitesEs400(int length) throws Exception {
        expectProblem(createOffer(body("a".repeat(length), null)), 400, "validation-error")
                .andExpect(jsonPath("$.errors[*].field", hasItem("rawText")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_offers WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    void losLimitesDelTextoSonInclusivos() throws Exception {
        createOffer(body("a".repeat(200), null)).andExpect(status().isCreated());
        createOffer(body("a".repeat(20_000), null)).andExpect(status().isCreated());
    }

    @Test
    void elTextoSeMideEnCaracteresComoEnLaBaseDeDatos() throws Exception {
        // 199 emojis son 398 unidades UTF-16 pero 199 caracteres para PostgreSQL (char_length): no llega al mínimo.
        expectProblem(createOffer(body("😀".repeat(199), null)), 400, "validation-error");
        createOffer(body("😀".repeat(200), null)).andExpect(status().isCreated());
    }

    @Test
    void faltaElTextoEs400() throws Exception {
        expectProblem(createOffer("{\"sourceUrl\": \"https://acme.dev\"}"), 400, "validation-error")
                .andExpect(jsonPath("$.errors[*].field", hasItem("rawText")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://acme.dev/oferta", "no es una url", "https://"})
    void unaUrlQueNoEsHttpEs400(String url) throws Exception {
        expectProblem(createOffer(body(RAW_TEXT, url)), 400, "validation-error")
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("sourceUrl"))
                .andExpect(jsonPath("$.errors[0].message").value("debe ser una URL http o https"));
    }

    @Test
    void unaUrlDemasiadoLargaEs400() throws Exception {
        String url = "https://acme.dev/" + "a".repeat(2048 - "https://acme.dev/".length() + 1);
        expectProblem(createOffer(body(RAW_TEXT, url)), 400, "validation-error");
    }

    @Test
    void unaUrlVaciaSeGuardaComoNull() throws Exception {
        createOffer(body(RAW_TEXT, "")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceUrl").value(nullValue()));
    }

    // ---- CA 7: autenticación ----

    @Test
    void sinTokenTodosLosEndpointsDan401() throws Exception {
        UUID id = UUID.randomUUID();
        expectProblem(perform(post(OFFERS).contentType(MediaType.APPLICATION_JSON).content(body(RAW_TEXT, null)),
                null), 401, "unauthorized");
        expectProblem(perform(get(OFFERS), null), 401, "unauthorized");
        expectProblem(perform(get(OFFERS + "/" + id), null), 401, "unauthorized");
        expectProblem(retry(id, null), 401, "unauthorized");
    }

    // ---- CA 8: propiedad de los datos ----

    @Test
    void unUsuarioNoPuedeVerNiReintentarLasOfertasDeOtro() throws Exception {
        UUID failed = failedOffer();
        UUID completed = createdId(createOffer(body(RAW_TEXT, null)));
        String other = tokenFor(rows.user());

        expectProblem(perform(get(OFFERS + "/" + completed), other), 404, "not-found");
        expectProblem(perform(get(OFFERS + "/" + failed), other), 404, "not-found");
        expectProblem(retry(failed, other), 404, "not-found");
        expectProblem(retry(completed, other), 404, "not-found");
        perform(get(OFFERS), other).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        assertThat(statusInDb(failed)).isEqualTo("FALLIDA");
    }

    @Test
    void unaOfertaQueNoExisteEs404() throws Exception {
        expectProblem(perform(get(OFFERS + "/" + UUID.randomUUID()), token), 404, "not-found");
        expectProblem(retry(UUID.randomUUID(), token), 404, "not-found");
    }

    // ---- CA 9: dashboard ----

    private UUID offerCreatedAt(String createdAt, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO job_offers (id, user_id, title, company, raw_text, extraction_status, created_at)
                VALUES (?, ?, ?, 'ACME', ?, 'COMPLETADA', ?)""",
                id, userId, title, RAW_TEXT, Timestamp.from(Instant.parse(createdAt)));
        return id;
    }

    private void analysisAt(UUID offerId, UUID profileId, int score, boolean blocking, String createdAt) {
        jdbc.update("""
                INSERT INTO match_analyses (id, offer_id, profile_id, score, has_blocking_gap, llm_model, created_at)
                VALUES (?, ?, ?, ?, ?, 'fake', ?)""",
                UUID.randomUUID(), offerId, profileId, score, blocking, Timestamp.from(Instant.parse(createdAt)));
    }

    @Test
    void elDashboardOrdenaDeMasRecienteAMasAntiguaConElUltimoAnalisisYLaCandidatura() throws Exception {
        UUID profile = rows.profile(userId);
        UUID oldest = offerCreatedAt("2026-11-01T10:00:00Z", "Antigua");
        UUID middle = offerCreatedAt("2026-11-02T10:00:00Z", "Media");
        UUID newest = offerCreatedAt("2026-11-03T10:00:00Z", "Nueva");
        analysisAt(middle, profile, 40, true, "2026-11-02T11:00:00Z");
        analysisAt(middle, profile, 85, false, "2026-11-02T12:00:00Z");
        rows.application(userId, oldest);

        perform(get(OFFERS), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(newest.toString()))
                .andExpect(jsonPath("$[0].latestScore").value(nullValue()))
                .andExpect(jsonPath("$[0].applicationStatus").value(nullValue()))
                .andExpect(jsonPath("$[1].id").value(middle.toString()))
                .andExpect(jsonPath("$[1].title").value("Media"))
                .andExpect(jsonPath("$[1].company").value("ACME"))
                .andExpect(jsonPath("$[1].latestScore").value(85))
                .andExpect(jsonPath("$[1].hasBlockingGap").value(false))
                .andExpect(jsonPath("$[1].createdAt").value("2026-11-02T10:00:00Z"))
                .andExpect(jsonPath("$[2].id").value(oldest.toString()))
                .andExpect(jsonPath("$[2].applicationStatus").value("GUARDADA"));
    }

    // ---- CA 13: documentación ----

    @Test
    void losEndpointsAparecenEnOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/offers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/offers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/offers/{offerId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/offers/{offerId}/extraction'].post").exists());
    }
}
