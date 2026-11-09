package com.jobpilot.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.auth.service.TokenService;
import com.jobpilot.shared.config.ClockConfig;
import com.jobpilot.support.PostgresTestcontainer;
import com.jobpilot.support.TestRows;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * JP-BE-08 de punta a punta (API → servicio → BD): tablero, alta, edición, reordenación y cambios de estado con
 * {@code ApplicationStatusPolicy}. El reloj es fijo para que "hoy" no cambie a mitad de la ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class ApplicationControllerIT {

    private static final String APPLICATIONS = "/api/v1/applications";
    private static final String PROBLEMS = "https://jobpilot.dev/problems/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Clock FIXED = Clock.fixed(Instant.now(), ClockConfig.ZONE);
    private static final LocalDate TODAY = LocalDate.now(FIXED);

    @TestBean
    private Clock clock;

    static Clock clock() {
        return FIXED;
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    private TestRows rows;
    private UUID userId;
    private String token;

    @BeforeEach
    void user() {
        rows = new TestRows(jdbc);
        userId = rows.user();
        token = tokenFor(userId);
    }

    // ---- Utilidades ----

    private String tokenFor(UUID user) {
        return "Bearer " + tokens.issue(user).value();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String authorization) throws Exception {
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private ResultActions board() throws Exception {
        return perform(get(APPLICATIONS), token);
    }

    private ResultActions create(String body) throws Exception {
        return perform(post(APPLICATIONS).contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    private ResultActions create(UUID offerId) throws Exception {
        return create("{\"offerId\": \"" + offerId + "\"}");
    }

    private ResultActions update(UUID applicationId, String body) throws Exception {
        return update(applicationId, body, token);
    }

    private ResultActions update(UUID applicationId, String body, String authorization) throws Exception {
        return perform(patch(APPLICATIONS + "/" + applicationId).contentType(MediaType.APPLICATION_JSON)
                .content(body), authorization);
    }

    private ResultActions changeStatus(UUID applicationId, String status, int position, long version)
            throws Exception {
        return changeStatus(applicationId, status, position, version, token);
    }

    private ResultActions changeStatus(UUID applicationId, String status, int position, long version,
            String authorization) throws Exception {
        String body = "{\"status\": \"" + status + "\", \"boardPosition\": " + position + ", \"version\": "
                + version + "}";
        return perform(patch(APPLICATIONS + "/" + applicationId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(body), authorization);
    }

    private static JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private UUID offer(UUID owner, String title, String company) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO job_offers (id, user_id, title, company, raw_text, extraction_status)
                VALUES (?, ?, ?, ?, ?, 'COMPLETADA')""", id, owner, title, company, TestRows.VALID_RAW_TEXT);
        return id;
    }

    private UUID offer() {
        return offer(userId, "Puesto", "Empresa");
    }

    private UUID application(UUID offerId, String status, int position, LocalDate appliedAt, LocalDate followUp) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO applications (id, user_id, offer_id, status, board_position, applied_at, follow_up_date)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", id, userId, offerId, status, position, appliedAt, followUp);
        return id;
    }

    private UUID application(String status, int position) {
        LocalDate appliedAt = List.of("ENVIADA", "ENTREVISTA", "OFERTA").contains(status) ? TODAY.minusDays(5) : null;
        return application(offer(), status, position, appliedAt, null);
    }

    private Map<String, Object> stored(UUID applicationId) {
        return jdbc.queryForMap("SELECT * FROM applications WHERE id = ?", applicationId);
    }

    private int position(UUID applicationId) {
        return ((Number) stored(applicationId).get("board_position")).intValue();
    }

    private long version(UUID applicationId) {
        return ((Number) stored(applicationId).get("version")).longValue();
    }

    private List<Map<String, Object>> history(UUID applicationId) {
        return jdbc.queryForList("""
                SELECT from_status, to_status FROM application_status_history
                WHERE application_id = ?""", applicationId);
    }

    // ---- Seguridad ----

    @Test
    void requiresAuthentication() throws Exception {
        perform(get(APPLICATIONS), null).andExpect(status().isUnauthorized());
        perform(post(APPLICATIONS).contentType(MediaType.APPLICATION_JSON).content("{}"), null)
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /applications ----

    @Test
    void boardIsEmptyForANewUser() throws Exception {
        board().andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void boardListsOnlyMyApplicationsInBoardOrder() throws Exception {
        UUID descartada = application("DESCARTADA", 0);
        UUID oferta = application("OFERTA", 0);
        UUID enviada1 = application("ENVIADA", 1);
        UUID enviada0 = application("ENVIADA", 0);
        UUID entrevista = application("ENTREVISTA", 0);
        UUID guardada = application("GUARDADA", 0);
        UUID other = rows.user();
        rows.application(other, offer(other, "Ajena", "Otra"));

        JsonNode body = json(board().andExpect(status().isOk()));

        assertThat(body.findValuesAsString("id")).containsExactly(guardada.toString(), enviada0.toString(),
                enviada1.toString(), entrevista.toString(), oferta.toString(), descartada.toString());
    }

    @Test
    void cardShowsOfferDataLatestScoreAndOverdueFollowUp() throws Exception {
        UUID offerId = offer(userId, "Desarrollador/a Java Junior", "Acme");
        UUID profile = rows.profile(userId);
        UUID older = rows.analysis(offerId, profile, 42);
        jdbc.update("UPDATE match_analyses SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(3600)), older);
        rows.analysis(offerId, profile, 85);
        UUID id = application(offerId, "ENVIADA", 0, TODAY.minusDays(10), TODAY.minusDays(1));
        jdbc.update("UPDATE applications SET notes = 'Llamar' WHERE id = ?", id);

        board().andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].offerId").value(offerId.toString()))
                .andExpect(jsonPath("$[0].offerTitle").value("Desarrollador/a Java Junior"))
                .andExpect(jsonPath("$[0].company").value("Acme"))
                .andExpect(jsonPath("$[0].score").value(85))
                .andExpect(jsonPath("$[0].status").value("ENVIADA"))
                .andExpect(jsonPath("$[0].boardPosition").value(0))
                .andExpect(jsonPath("$[0].appliedAt").value(TODAY.minusDays(10).toString()))
                .andExpect(jsonPath("$[0].followUpDate").value(TODAY.minusDays(1).toString()))
                .andExpect(jsonPath("$[0].followUpOverdue").value(true))
                .andExpect(jsonPath("$[0].notes").value("Llamar"))
                .andExpect(jsonPath("$[0].version").value(0));
    }

    @Test
    void followUpIsNotOverdueTodayNorInClosedColumns() throws Exception {
        application(offer(), "GUARDADA", 0, null, TODAY);
        application(offer(), "OFERTA", 0, TODAY.minusDays(9), TODAY.minusDays(3));
        application(offer(), "DESCARTADA", 0, null, TODAY.minusDays(3));

        board().andExpect(status().isOk())
                .andExpect(jsonPath("$[0].followUpOverdue").value(false))
                .andExpect(jsonPath("$[1].followUpOverdue").value(false))
                .andExpect(jsonPath("$[2].followUpOverdue").value(false));
    }

    @Test
    void cardWithoutAnalysisHasNoScore() throws Exception {
        application("GUARDADA", 0);

        board().andExpect(jsonPath("$[0].score").value(nullValue()))
                .andExpect(jsonPath("$[0].followUpOverdue").value(false));
    }

    // ---- POST /applications ----

    @Test
    void createsTheApplicationAtTheEndOfGuardadaAndRecordsIt() throws Exception {
        application("GUARDADA", 0);
        application("GUARDADA", 1);
        UUID offerId = offer(userId, "Backend", "Acme");

        JsonNode body = json(create(offerId)
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.offerId").value(offerId.toString()))
                .andExpect(jsonPath("$.offerTitle").value("Backend"))
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.status").value("GUARDADA"))
                .andExpect(jsonPath("$.boardPosition").value(2))
                .andExpect(jsonPath("$.appliedAt").value(nullValue()))
                .andExpect(jsonPath("$.followUpOverdue").value(false))
                .andExpect(jsonPath("$.version").value(0)));

        UUID id = UUID.fromString(body.get("id").asString());
        assertThat(stored(id).get("user_id")).isEqualTo(userId);
        assertThat(history(id)).singleElement().satisfies(entry -> {
            assertThat(entry.get("from_status")).isNull();
            assertThat(entry.get("to_status")).isEqualTo("GUARDADA");
        });
    }

    @Test
    void anOfferWhoseExtractionFailedCanAlsoBeSaved() throws Exception {
        UUID offerId = UUID.randomUUID();
        jdbc.update("INSERT INTO job_offers (id, user_id, raw_text, extraction_status) VALUES (?, ?, ?, 'FALLIDA')",
                offerId, userId, TestRows.VALID_RAW_TEXT);

        create(offerId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.offerTitle").value(nullValue()))
                .andExpect(jsonPath("$.boardPosition").value(0));
    }

    @Test
    void anOfferCanOnlyBeSavedOnce() throws Exception {
        UUID offerId = offer();
        create(offerId).andExpect(status().isCreated());

        create(offerId).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "application-already-exists"))
                .andExpect(jsonPath("$.title").value("La oferta ya está en el tablero"))
                .andExpect(jsonPath("$.instance").value(APPLICATIONS));
    }

    @Test
    void cannotSaveAnOfferOfAnotherUserOrOneThatDoesNotExist() throws Exception {
        UUID other = rows.user();

        create(offer(other, "Ajena", "Otra")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "not-found"));
        create(UUID.randomUUID()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM applications WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    void createRequiresTheOfferId() throws Exception {
        create("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-error"))
                .andExpect(jsonPath("$.errors[0].field").value("offerId"));
    }

    // ---- PATCH /applications/{id}/status ----

    @Test
    void movingFromGuardadaToEnviadaSetsAppliedAtToTodayAndPersists() throws Exception {
        UUID id = application("GUARDADA", 0);

        changeStatus(id, "ENVIADA", 0, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENVIADA"))
                .andExpect(jsonPath("$.appliedAt").value(TODAY.toString()))
                .andExpect(jsonPath("$.boardPosition").value(0))
                .andExpect(jsonPath("$.version").value(1));

        board().andExpect(jsonPath("$[0].status").value("ENVIADA"))
                .andExpect(jsonPath("$[0].appliedAt").value(TODAY.toString()));
        assertThat(history(id)).singleElement().satisfies(entry -> {
            assertThat(entry.get("from_status")).isEqualTo("GUARDADA");
            assertThat(entry.get("to_status")).isEqualTo("ENVIADA");
        });
    }

    @Test
    void movingToEnviadaKeepsAnAppliedAtThatWasAlreadySet() throws Exception {
        LocalDate earlier = TODAY.minusDays(20);
        UUID id = application(offer(), "GUARDADA", 0, earlier, null);

        changeStatus(id, "ENVIADA", 0, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAt").value(earlier.toString()));
    }

    @Test
    void forbiddenTransitionIsRejectedAndNothingChanges() throws Exception {
        UUID id = application("OFERTA", 0);

        changeStatus(id, "GUARDADA", 0, 0).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-status-transition"))
                .andExpect(jsonPath("$.title").value("Transición de estado no permitida"))
                .andExpect(jsonPath("$.detail").value("No se puede pasar de OFERTA a GUARDADA."))
                .andExpect(jsonPath("$.instance").value(APPLICATIONS + "/" + id + "/status"));

        assertThat(stored(id).get("status")).isEqualTo("OFERTA");
        assertThat(version(id)).isZero();
        assertThat(history(id)).isEmpty();
    }

    @Test
    void skippingAStatusIsRejected() throws Exception {
        UUID id = application("GUARDADA", 0);

        changeStatus(id, "OFERTA", 0, 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No se puede pasar de GUARDADA a OFERTA."));
    }

    @Test
    void anOfferCanBeDiscardedAndADiscardedOneReopened() throws Exception {
        UUID id = application("OFERTA", 0);

        changeStatus(id, "DESCARTADA", 0, 0).andExpect(status().isOk());
        changeStatus(id, "GUARDADA", 0, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GUARDADA"));

        // Con el reloj fijo las dos filas tienen el mismo changed_at, así que no se comprueba por orden: se comprueba
        // que están exactamente estas dos transiciones (from → to), que encadenadas solo admiten este orden.
        assertThat(history(id)).extracting(entry -> entry.get("from_status") + "→" + entry.get("to_status"))
                .containsExactlyInAnyOrder("OFERTA→DESCARTADA", "DESCARTADA→GUARDADA");
    }

    @Test
    void sameStatusIsABadRequest() throws Exception {
        UUID id = application("ENVIADA", 0);

        changeStatus(id, "ENVIADA", 0, 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-error"))
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        assertThat(history(id)).isEmpty();
    }

    @Test
    void staleVersionIsAConflict() throws Exception {
        UUID id = application("GUARDADA", 0);
        changeStatus(id, "ENVIADA", 0, 0).andExpect(status().isOk());

        changeStatus(id, "ENTREVISTA", 0, 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "version-conflict"))
                .andExpect(jsonPath("$.title").value("La candidatura ha cambiado"));
        assertThat(stored(id).get("status")).isEqualTo("ENVIADA");
    }

    @Test
    void cannotMoveAnApplicationOfAnotherUser() throws Exception {
        UUID id = application("GUARDADA", 0);
        String otherToken = tokenFor(rows.user());

        changeStatus(id, "ENVIADA", 0, 0, otherToken).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "not-found"));
        update(id, "{\"notes\": \"x\", \"version\": 0}", otherToken).andExpect(status().isNotFound());
        assertThat(stored(id).get("status")).isEqualTo("GUARDADA");
    }

    @Test
    void changeStatusValidatesTheBody() throws Exception {
        UUID id = application("GUARDADA", 0);

        perform(patch(APPLICATIONS + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"ENVIADA\", \"boardPosition\": -1}"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-error"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("boardPosition")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("version")));
        perform(patch(APPLICATIONS + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"ARCHIVADA\", \"boardPosition\": 0, \"version\": 0}"), token)
                .andExpect(status().isBadRequest());
    }

    @Test
    void movingBetweenColumnsRenumbersBothColumns() throws Exception {
        UUID g0 = application("GUARDADA", 0);
        UUID moved = application("GUARDADA", 1);
        UUID g2 = application("GUARDADA", 2);
        UUID e0 = application("ENVIADA", 0);
        UUID e1 = application("ENVIADA", 1);

        changeStatus(moved, "ENVIADA", 1, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.boardPosition").value(1));

        assertThat(List.of(position(e0), position(moved), position(e1))).containsExactly(0, 1, 2);
        assertThat(List.of(position(g0), position(g2))).containsExactly(0, 1);
    }

    @Test
    void anIndexBeyondTheColumnSizePutsTheCardLast() throws Exception {
        UUID e0 = application("ENVIADA", 0);
        UUID moved = application("GUARDADA", 0);

        changeStatus(moved, "ENVIADA", 50, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.boardPosition").value(1));
        assertThat(position(e0)).isZero();
    }

    // ---- PATCH /applications/{id} ----

    @Test
    void reorderingWithinAColumnRenumbersItAndDoesNotTouchTheHistory() throws Exception {
        UUID a = application("GUARDADA", 0);
        UUID b = application("GUARDADA", 1);
        UUID c = application("GUARDADA", 2);

        update(c, "{\"boardPosition\": 0, \"version\": 0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.boardPosition").value(0))
                .andExpect(jsonPath("$.status").value("GUARDADA"));

        assertThat(List.of(position(c), position(a), position(b))).containsExactly(0, 1, 2);
        JsonNode body = json(board());
        assertThat(body.findValuesAsString("id")).containsExactly(c.toString(), a.toString(), b.toString());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM application_status_history h JOIN applications a ON a.id = h.application_id
                WHERE a.user_id = ?""", Integer.class, userId)).isZero();
    }

    @Test
    void reorderingBeyondTheEndPutsTheCardLast() throws Exception {
        UUID a = application("GUARDADA", 0);
        UUID b = application("GUARDADA", 1);

        update(a, "{\"boardPosition\": 9, \"version\": 0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.boardPosition").value(1));
        assertThat(position(b)).isZero();
    }

    @Test
    void updateOnlyChangesTheFieldsThatAreSent() throws Exception {
        UUID id = application(offer(), "ENVIADA", 0, TODAY.minusDays(4), TODAY.plusDays(3));
        jdbc.update("UPDATE applications SET notes = 'Notas previas' WHERE id = ?", id);

        update(id, "{\"notes\": \"Escribir a RR. HH.\", \"version\": 0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Escribir a RR. HH."))
                .andExpect(jsonPath("$.appliedAt").value(TODAY.minusDays(4).toString()))
                .andExpect(jsonPath("$.followUpDate").value(TODAY.plusDays(3).toString()))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void sendingNullClearsAField() throws Exception {
        UUID id = application(offer(), "ENVIADA", 0, TODAY.minusDays(4), TODAY.plusDays(3));
        jdbc.update("UPDATE applications SET notes = 'Notas previas' WHERE id = ?", id);

        update(id, "{\"notes\": null, \"followUpDate\": null, \"version\": 0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value(nullValue()))
                .andExpect(jsonPath("$.followUpDate").value(nullValue()))
                .andExpect(jsonPath("$.appliedAt").value(TODAY.minusDays(4).toString()));
        assertThat(stored(id).get("notes")).isNull();
    }

    @Test
    void appliedAtCannotBeClearedOnceTheApplicationWasSent() throws Exception {
        UUID id = application("ENTREVISTA", 0);

        update(id, "{\"appliedAt\": null, \"version\": 0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-error"))
                .andExpect(jsonPath("$.errors[0].field").value("appliedAt"));
        assertThat(stored(id).get("applied_at")).isNotNull();
    }

    @Test
    void appliedAtCanBeSetAndClearedWhileSaved() throws Exception {
        UUID id = application("GUARDADA", 0);

        update(id, "{\"appliedAt\": \"" + TODAY + "\", \"version\": 0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAt").value(TODAY.toString()));
        update(id, "{\"appliedAt\": null, \"version\": 1}").andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAt").value(nullValue()));
    }

    @Test
    void updateWithAStaleVersionIsAConflict() throws Exception {
        UUID id = application("GUARDADA", 0);
        update(id, "{\"notes\": \"Primera\", \"version\": 0}").andExpect(status().isOk());

        update(id, "{\"notes\": \"Segunda\", \"version\": 0}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "version-conflict"));
        assertThat(stored(id).get("notes")).isEqualTo("Primera");
    }

    @Test
    void updateValidatesTheBody() throws Exception {
        UUID id = application("GUARDADA", 0);

        update(id, "{\"notes\": \"x\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        update(id, "{\"boardPosition\": -1, \"version\": 0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("boardPosition"));
        update(id, "{\"followUpDate\": \"no-es-una-fecha\", \"version\": 0}").andExpect(status().isBadRequest());
    }

    @Test
    void notesLongerThan5000CharactersAreRejectedInsideFieldUpdate() throws Exception {
        UUID id = application("GUARDADA", 0);

        update(id, "{\"notes\": \"" + "a".repeat(5001) + "\", \"version\": 0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-error"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("notes"));
        assertThat(stored(id).get("notes")).isNull();
        assertThat(version(id)).isZero();

        update(id, "{\"notes\": \"" + "a".repeat(5000) + "\", \"version\": 0}").andExpect(status().isOk());
    }

    // ---- OpenAPI ----

    @Test
    void endpointsAreDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/applications'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/applications'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/applications/{applicationId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/applications/{applicationId}/status'].patch").exists());
    }
}
