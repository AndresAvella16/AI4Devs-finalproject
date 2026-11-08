package com.jobpilot.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.auth.service.TokenService;
import com.jobpilot.support.PostgresTestcontainer;
import com.jobpilot.support.TestRows;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * CA 11 y 12: límites de peticiones (sección 2.5) con los límites bajados para el test: 2 llamadas de IA por
 * usuario y 3 intentos de login por IP + email. Los casos de {@code X-Forwarded-For} van por HTTP real, porque es
 * Tomcat ({@code server.forward-headers-strategy: native}) quien lo interpreta; MockMvc no pasa por Tomcat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jobpilot.rate-limit.ai.capacity=2",
        "jobpilot.rate-limit.ai.period=1h",
        "jobpilot.rate-limit.login.capacity=3",
        "jobpilot.rate-limit.login.period=1m"})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class RateLimitIT {

    private static final String RATE_LIMITED = "https://jobpilot.dev/problems/rate-limit-exceeded";
    private static final String RAW_TEXT = "Buscamos Desarrollador/a Java Junior en Valencia. ".repeat(6);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    @LocalServerPort
    private int port;

    private String token(UUID userId) {
        return "Bearer " + tokens.issue(userId).value();
    }

    private ResultActions createOffer(String token, String rawText) throws Exception {
        return mockMvc.perform(post("/api/v1/offers")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("rawText", rawText))));
    }

    private int offersOf(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM job_offers WHERE user_id = ?", Integer.class, userId);
    }

    private UUID failedOffer(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO job_offers (id, user_id, raw_text, extraction_status) VALUES (?, ?, ?, 'FALLIDA')",
                id, userId, RAW_TEXT);
        return id;
    }

    private static void expectRateLimited(ResultActions result) throws Exception {
        result.andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(RATE_LIMITED))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // ---- CA 11: IA, por usuario ----

    @Test
    void laTerceraImportacionDeUnUsuarioEs429YNoSeGuardaLaOferta() throws Exception {
        UUID user = new TestRows(jdbc).user();
        String token = token(user);
        createOffer(token, RAW_TEXT).andExpect(status().isCreated());
        createOffer(token, RAW_TEXT).andExpect(status().isCreated());

        ResultActions limited = createOffer(token, RAW_TEXT);

        expectRateLimited(limited);
        // 2 por hora con recarga progresiva: la siguiente llega en 30 minutos.
        limited.andExpect(header().string(HttpHeaders.RETRY_AFTER, "1800"))
                .andExpect(jsonPath("$.instance").value("/api/v1/offers"));
        assertThat(offersOf(user)).isEqualTo(2);
    }

    @Test
    void lasPeticionesNoValidasNoGastanCupo() throws Exception {
        UUID user = new TestRows(jdbc).user();
        String token = token(user);
        for (int i = 0; i < 3; i++) {
            createOffer(token, "corto").andExpect(status().isBadRequest());
        }

        createOffer(token, RAW_TEXT).andExpect(status().isCreated());
        createOffer(token, RAW_TEXT).andExpect(status().isCreated());
    }

    @Test
    void elReintentoDeExtraccionCompartePresupuestoConLaImportacion() throws Exception {
        UUID user = new TestRows(jdbc).user();
        String token = token(user);
        createOffer(token, RAW_TEXT).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/offers/" + failedOffer(user) + "/extraction")
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());

        UUID stillFailed = failedOffer(user);
        expectRateLimited(mockMvc.perform(post("/api/v1/offers/" + stillFailed + "/extraction")
                .header(HttpHeaders.AUTHORIZATION, token)));
        assertThat(jdbc.queryForObject("SELECT extraction_status FROM job_offers WHERE id = ?", String.class,
                stillFailed)).isEqualTo("FALLIDA");
    }

    @Test
    void cadaUsuarioTieneSuPropioCupo() throws Exception {
        TestRows rows = new TestRows(jdbc);
        String first = token(rows.user());
        createOffer(first, RAW_TEXT).andExpect(status().isCreated());
        createOffer(first, RAW_TEXT).andExpect(status().isCreated());
        expectRateLimited(createOffer(first, RAW_TEXT));

        createOffer(token(rows.user()), RAW_TEXT).andExpect(status().isCreated());
    }

    // ---- CA 12: login, por IP + email ----

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", password, "fullName", "Ana"))))
                .andExpect(status().isCreated());
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.dev";
    }

    @Test
    void elCuartoIntentoDeLoginEs429AunqueLaContrasenaSeaCorrecta() throws Exception {
        String email = uniqueEmail();
        register(email, "Correcta123");
        for (int i = 0; i < 3; i++) {
            login(email, "Incorrecta123").andExpect(status().isUnauthorized());
        }

        ResultActions limited = login(email, "Correcta123");

        expectRateLimited(limited);
        // 3 por minuto: el siguiente intento llega en 20 s.
        limited.andExpect(header().string(HttpHeaders.RETRY_AFTER, "20"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void losIntentosCorrectosTambienCuentan() throws Exception {
        String email = uniqueEmail();
        register(email, "Correcta123");
        for (int i = 0; i < 3; i++) {
            login(email, "Correcta123").andExpect(status().isOk());
        }

        expectRateLimited(login(email, "Correcta123"));
    }

    @Test
    void elEmailSeNormalizaAntesDeContar() throws Exception {
        String email = uniqueEmail();
        login(email, "Incorrecta123").andExpect(status().isUnauthorized());
        login(email.toUpperCase(), "Incorrecta123").andExpect(status().isUnauthorized());
        login("User-" + email.substring("user-".length()), "Incorrecta123").andExpect(status().isUnauthorized());

        expectRateLimited(login(email, "Incorrecta123"));
    }

    @Test
    void otroEmailDesdeLaMismaIpNoComparteCupo() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 3; i++) {
            login(email, "Incorrecta123").andExpect(status().isUnauthorized());
        }
        expectRateLimited(login(email, "Incorrecta123"));

        login(uniqueEmail(), "Incorrecta123").andExpect(status().isUnauthorized());
    }

    @Test
    void unLoginMalFormadoNoGastaCupo() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"" + email + "\"}")).andExpect(status().isBadRequest());
        }

        login(email, "Incorrecta123").andExpect(status().isUnauthorized());
    }

    // ---- CA 12 con proxy: la IP sale de X-Forwarded-For cuando lo envía un proxy interno (nginx) ----

    private ResponseEntity<Void> httpLogin(String email, String forwardedFor) {
        return RestClient.create("http://localhost:" + port).post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", forwardedFor)
                .body(JSON.writeValueAsString(Map.of("email", email, "password", "Incorrecta123")))
                .retrieve()
                .onStatus(code -> true, (request, response) -> { })
                .toBodilessEntity();
    }

    @Test
    void detrasDeUnProxyInternoCadaIpClienteTieneSuPropioCupo() {
        String email = uniqueEmail();
        for (int i = 0; i < 3; i++) {
            assertThat(httpLogin(email, "203.0.113.10").getStatusCode().value()).isEqualTo(401);
        }
        assertThat(httpLogin(email, "203.0.113.10").getStatusCode().value()).isEqualTo(429);

        // Mismo email desde otro cliente: sin forward-headers-strategy los dos serían 127.0.0.1 y compartirían cupo.
        assertThat(httpLogin(email, "203.0.113.20").getStatusCode().value()).isEqualTo(401);
    }
}
