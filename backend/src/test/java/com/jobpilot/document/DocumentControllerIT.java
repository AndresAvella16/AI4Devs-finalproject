package com.jobpilot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.ai.DocumentContext;
import com.jobpilot.ai.LlmGateway;
import com.jobpilot.auth.service.TokenService;
import com.jobpilot.shared.error.LlmInvalidResponseException;
import com.jobpilot.shared.error.LlmOverloadedException;
import com.jobpilot.shared.error.LlmTimeoutException;
import com.jobpilot.support.PostgresTestcontainer;
import com.jobpilot.support.TestRows;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
 * JP-BE-07 de punta a punta (API → servicio → BD) con {@code FakeLlmGateway}: la oferta se importa con
 * {@code POST /offers}, se analiza con {@code POST /offers/{id}/analysis} y sobre ella se generan, editan y exportan
 * los documentos. Los fallos del LLM se simulan espiando ese mismo bean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class DocumentControllerIT {

    private static final String OFFERS = "/api/v1/offers";
    private static final String DOCUMENTS = "/api/v1/documents";
    private static final String PROBLEMS = "https://jobpilot.dev/problems/";
    private static final String RAW_TEXT = "Buscamos Desarrollador/a Java Junior en Valencia. ".repeat(6);
    private static final String FULL_NAME = "Lucía Ferrándiz Núñez";
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
    private String email;
    private String token;

    @BeforeEach
    void userWithProfile() {
        rows = new TestRows(jdbc);
        userId = rows.user();
        jdbc.update("UPDATE users SET full_name = ? WHERE id = ?", FULL_NAME, userId);
        email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
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

    private static JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String documentsPath(UUID offerId) {
        return OFFERS + "/" + offerId + "/documents";
    }

    private UUID importedOffer(String authorization) throws Exception {
        ResultActions created = perform(post(OFFERS).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("rawText", RAW_TEXT))), authorization)
                .andExpect(status().isCreated());
        return UUID.fromString(json(created).get("id").asString());
    }

    /** Oferta importada y analizada (la oferta fake del Ejemplo 1, puntuación 85). */
    private UUID analyzedOffer() throws Exception {
        UUID offerId = importedOffer(token);
        perform(post(OFFERS + "/" + offerId + "/analysis"), token).andExpect(status().isCreated());
        return offerId;
    }

    private ResultActions generate(UUID offerId, String type, String authorization) throws Exception {
        return perform(post(documentsPath(offerId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"" + type + "\"}"), authorization);
    }

    private JsonNode generated(UUID offerId, String type) throws Exception {
        return json(generate(offerId, type, token).andExpect(status().isCreated()));
    }

    private ResultActions history(UUID offerId, String type, String authorization) throws Exception {
        return perform(get(documentsPath(offerId)).param("type", type), authorization);
    }

    private ResultActions createVersion(String documentId, String content, String authorization) throws Exception {
        return perform(post(DOCUMENTS + "/" + documentId + "/versions").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("contentMarkdown", content))), authorization);
    }

    private ResultActions pdf(String documentId, String authorization) throws Exception {
        return perform(get(DOCUMENTS + "/" + documentId + "/pdf"), authorization);
    }

    private static ResultActions expectProblem(ResultActions result, int status, String slug) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + slug));
    }

    private int documentsInDb() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM generated_documents d JOIN job_offers o ON o.id = d.offer_id
                WHERE o.user_id = ?""", Integer.class, userId);
    }

    // ---- CA 1: generar la carta (HU-04, "Generación de la carta") ----

    @Test
    void generarLaCartaDevuelve201ConLaVersion1FirmadaPorElServidorYLaGuarda() throws Exception {
        UUID offerId = analyzedOffer();

        JsonNode body = json(generate(offerId, "CARTA", token)
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.type").value("CARTA"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.editedByUser").value(false))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.contentMarkdown").value(containsString("Desarrollador/a Java Junior")))
                .andExpect(jsonPath("$.contentMarkdown").value(endsWith("Atentamente,\n\n" + FULL_NAME))));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT offer_id, type, version, edited_by_user, content_markdown FROM generated_documents WHERE id = ?",
                UUID.fromString(body.get("id").asString()));
        assertThat(row.get("offer_id")).isEqualTo(offerId);
        assertThat(row.get("type")).isEqualTo("CARTA");
        assertThat(((Number) row.get("version")).intValue()).isEqualTo(1);
        assertThat(row.get("edited_by_user")).isEqualTo(false);
        assertThat(row.get("content_markdown")).isEqualTo(body.get("contentMarkdown").asString());
    }

    @Test
    void laIaNoRecibeElNombreNiElEmailPeroLaVersionGuardadaLlevaLaFirma() throws Exception {
        UUID offerId = analyzedOffer();

        generated(offerId, "CARTA");

        ArgumentCaptor<DocumentContext> context = ArgumentCaptor.captor();
        verify(llm).generateDocument(context.capture());
        assertThat(context.getValue().toString()).doesNotContain(FULL_NAME).doesNotContain("Lucía")
                .doesNotContain(email);
        assertThat(jdbc.queryForObject("SELECT content_markdown FROM generated_documents WHERE offer_id = ?",
                String.class, offerId)).endsWith("\n\n" + FULL_NAME);
    }

    // ---- CA 2: versiones independientes por tipo; el CV no se firma ----

    @Test
    void regenerarCreaLaVersionSiguienteYCadaTipoTieneSuPropiaNumeracion() throws Exception {
        UUID offerId = analyzedOffer();

        generated(offerId, "CARTA");
        generate(offerId, "CARTA", token).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2));
        generate(offerId, "CV_ADAPTADO", token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CV_ADAPTADO"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.contentMarkdown").value(not(containsString(FULL_NAME))));

        assertThat(documentsInDb()).isEqualTo(3);
    }

    // ---- CA 3: 409 sin análisis, 404, 400, 401 ----

    @Test
    void sinAnalisisEs409AnalysisRequiredYNoSeLlamaALaIa() throws Exception {
        UUID offerId = importedOffer(token);

        expectProblem(generate(offerId, "CARTA", token), 409, "analysis-required")
                .andExpect(jsonPath("$.title").value("La oferta no tiene análisis de encaje"))
                .andExpect(jsonPath("$.instance").value(documentsPath(offerId)));
        verify(llm, never()).generateDocument(any());
        assertThat(documentsInDb()).isZero();
    }

    @Test
    void generarSobreUnaOfertaAjenaOInexistenteEs404() throws Exception {
        UUID offerId = analyzedOffer();
        UUID other = rows.user();
        rows.profile(other);

        expectProblem(generate(offerId, "CARTA", tokenFor(other)), 404, "not-found");
        expectProblem(generate(UUID.randomUUID(), "CARTA", token), 404, "not-found");
        verify(llm, never()).generateDocument(any());
    }

    @Test
    void unTipoQueFaltaONoEsValidoEs400() throws Exception {
        UUID offerId = analyzedOffer();

        perform(post(documentsPath(offerId)).contentType(MediaType.APPLICATION_JSON).content("{}"), token)
                .andExpect(status().isBadRequest());
        generate(offerId, "POEMA", token).andExpect(status().isBadRequest());
        verify(llm, never()).generateDocument(any());
    }

    @Test
    void sinTokenEs401() throws Exception {
        UUID id = UUID.randomUUID();
        expectProblem(generate(id, "CARTA", null), 401, "unauthorized");
        expectProblem(history(id, "CARTA", null), 401, "unauthorized");
        expectProblem(createVersion(id.toString(), "Texto", null), 401, "unauthorized");
        expectProblem(pdf(id.toString(), null), 401, "unauthorized");
    }

    // ---- CA 4: errores de la IA, sin guardar nada ----

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
        UUID offerId = analyzedOffer();
        doThrow(error).when(llm).generateDocument(any());

        expectProblem(generate(offerId, "CARTA", token), status, slug);
        assertThat(documentsInDb()).isZero();
    }

    // ---- CA 6: historial ----

    @Test
    void elHistorialDevuelveLaUltimaVersionCompletaYLasVersionesDeMasRecienteAMasAntiguaSinContenido()
            throws Exception {
        UUID offerId = analyzedOffer();
        String first = generated(offerId, "CARTA").get("id").asString();
        String edited = json(createVersion(first, "Mi carta editada", token).andExpect(status().isCreated()))
                .get("id").asString();
        generated(offerId, "CV_ADAPTADO");

        history(offerId, "CARTA", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latest.id").value(edited))
                .andExpect(jsonPath("$.latest.version").value(2))
                .andExpect(jsonPath("$.latest.type").value("CARTA"))
                .andExpect(jsonPath("$.latest.editedByUser").value(true))
                .andExpect(jsonPath("$.latest.contentMarkdown").value("Mi carta editada"))
                .andExpect(jsonPath("$.versions", hasSize(2)))
                .andExpect(jsonPath("$.versions[0].id").value(edited))
                .andExpect(jsonPath("$.versions[0].version").value(2))
                .andExpect(jsonPath("$.versions[0].editedByUser").value(true))
                .andExpect(jsonPath("$.versions[0].createdAt").isString())
                .andExpect(jsonPath("$.versions[0].contentMarkdown").doesNotExist())
                .andExpect(jsonPath("$.versions[1].id").value(first))
                .andExpect(jsonPath("$.versions[1].version").value(1))
                .andExpect(jsonPath("$.versions[1].editedByUser").value(false));
    }

    @Test
    void elHistorialSinDocumentosDeEseTipoOAjenoEs404() throws Exception {
        UUID offerId = analyzedOffer();
        generated(offerId, "CARTA");
        UUID other = rows.user();

        expectProblem(history(offerId, "CV_ADAPTADO", token), 404, "not-found");
        expectProblem(history(offerId, "CARTA", tokenFor(other)), 404, "not-found");
        expectProblem(history(UUID.randomUUID(), "CARTA", token), 404, "not-found");
    }

    @Test
    void elHistorialSinTipoOConUnTipoNoValidoEs400() throws Exception {
        UUID offerId = analyzedOffer();

        perform(get(documentsPath(offerId)), token).andExpect(status().isBadRequest());
        history(offerId, "POEMA", token).andExpect(status().isBadRequest());
    }

    // ---- CA 7 y 8: edición y bloqueo optimista (HU-04, "Edición y versionado") ----

    @Test
    void editarCreaUnaVersionNuevaMarcadaComoEditadaPorElUsuario() throws Exception {
        UUID offerId = analyzedOffer();
        String first = generated(offerId, "CARTA").get("id").asString();

        JsonNode body = json(createVersion(first, "# Mi carta\n\nEditada a mano.", token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CARTA"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.editedByUser").value(true))
                .andExpect(jsonPath("$.contentMarkdown").value("# Mi carta\n\nEditada a mano.")));

        assertThat(body.get("id").asString()).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT edited_by_user FROM generated_documents WHERE id = ?::uuid",
                Boolean.class, body.get("id").asString())).isTrue();
        assertThat(jdbc.queryForObject("SELECT content_markdown FROM generated_documents WHERE id = ?::uuid",
                String.class, first)).as("la versión anterior no cambia").endsWith(FULL_NAME);
    }

    @Test
    void editarAPartirDeUnaVersionQueYaNoEsLaUltimaEs409YNoSeGuarda() throws Exception {
        UUID offerId = analyzedOffer();
        String first = generated(offerId, "CARTA").get("id").asString();
        createVersion(first, "Edición de la pestaña A", token).andExpect(status().isCreated());

        expectProblem(createVersion(first, "Edición de la pestaña B", token), 409, "document-version-conflict")
                .andExpect(jsonPath("$.title").value("El documento ha cambiado"));
        assertThat(documentsInDb()).isEqualTo(2);
    }

    @Test
    void regenerarDespuesDeEditarUsaLaVersionMasAlta() throws Exception {
        UUID offerId = analyzedOffer();
        String first = generated(offerId, "CARTA").get("id").asString();
        createVersion(first, "Editada", token).andExpect(status().isCreated());

        generate(offerId, "CARTA", token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.editedByUser").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void unContenidoVacioEs400(String content) throws Exception {
        String first = generated(analyzedOffer(), "CARTA").get("id").asString();

        expectProblem(createVersion(first, content, token), 400, "validation-error")
                .andExpect(jsonPath("$.errors[0].field").value("contentMarkdown"));
        assertThat(documentsInDb()).isEqualTo(1);
    }

    @Test
    void unContenidoDeMasDe20000CaracteresEs400YDe20000SeAcepta() throws Exception {
        String first = generated(analyzedOffer(), "CARTA").get("id").asString();

        expectProblem(createVersion(first, "a".repeat(20_001), token), 400, "validation-error");
        createVersion(first, "a".repeat(20_000), token).andExpect(status().isCreated());
    }

    @Test
    void editarUnDocumentoAjenoOInexistenteEs404() throws Exception {
        String first = generated(analyzedOffer(), "CARTA").get("id").asString();
        UUID other = rows.user();

        expectProblem(createVersion(first, "Robada", tokenFor(other)), 404, "not-found");
        expectProblem(createVersion(UUID.randomUUID().toString(), "Texto", token), 404, "not-found");
        assertThat(documentsInDb()).isEqualTo(1);
    }

    // ---- CA 9: PDF (HU-04, "Exportación") ----

    @Test
    void elPdfSeDescargaConElContenidoDeEsaVersionYElNombreEnLaCabecera() throws Exception {
        UUID offerId = analyzedOffer();
        String first = generated(offerId, "CARTA").get("id").asString();
        String edited = json(createVersion(first, "# Carta editada\n\nTexto con **negrita** y ñ.", token))
                .get("id").asString();

        byte[] bytes = pdf(edited, token)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("carta-v2.pdf")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);   // en orden de lectura: la negrita se dibuja aparte
            String text = stripper.getText(document);
            assertThat(text.strip()).startsWith(FULL_NAME);
            assertThat(text).contains("Carta editada").contains("Texto con negrita y ñ.")
                    .doesNotContain("Atentamente,");
        }
    }

    @Test
    void elPdfDelCvAdaptadoSeLlamaCvAdaptado() throws Exception {
        String cv = generated(analyzedOffer(), "CV_ADAPTADO").get("id").asString();

        pdf(cv, token).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("cv-adaptado-v1.pdf")));
    }

    @Test
    void elPdfDeUnDocumentoAjenoOInexistenteEs404() throws Exception {
        String first = generated(analyzedOffer(), "CARTA").get("id").asString();
        UUID other = rows.user();

        expectProblem(pdf(first, tokenFor(other)), 404, "not-found");
        expectProblem(pdf(UUID.randomUUID().toString(), token), 404, "not-found");
    }

    // ---- Documentación ----

    @Test
    void losEndpointsAparecenEnOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/offers/{offerId}/documents'].post.tags[0]").value("Documents"))
                .andExpect(jsonPath("$.paths['/api/v1/offers/{offerId}/documents'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/documents/{documentId}/versions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/documents/{documentId}/pdf'].get").exists());
    }
}
