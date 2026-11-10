package com.jobpilot.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.support.PostgresTestcontainer;
import com.jobpilot.support.TestRows;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * JP-QA-01: con el perfil e2e se cargan las migraciones y el seed de E2E ({@code db/e2e-seed}), que trae un usuario
 * con perfil para que el flujo de Playwright pueda analizar ofertas (aún no hay subida de CV: JP-BE-04). No se
 * carga el seed de dev, y el cupo de IA del perfil e2e permite ejecutar la suite muchas veces seguidas.
 */
@SpringBootTest(properties = "llm.mode=fake")
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@Import(PostgresTestcontainer.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)   // el estado inicial se comprueba antes de crear ofertas
class E2eSeedIT {

    static final String E2E_EMAIL = "e2e@jobpilot.test";
    static final String E2E_PASSWORD = "Demo1234!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Order(1)
    void cargaSoloElUsuarioDeE2eConPerfilYSinOfertas() {
        assertThat(jdbc.queryForList("SELECT email FROM users", String.class)).containsExactly(E2E_EMAIL);
        assertThat(count("SELECT count(*) FROM profiles p JOIN users u ON u.id = p.user_id WHERE u.email = ?"))
                .isOne();
        assertThat(count("""
                SELECT count(*) FROM profile_skills s JOIN profiles p ON p.id = s.profile_id
                JOIN users u ON u.id = p.user_id WHERE u.email = ?""")).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_offers", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM applications", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE script LIKE '%demo_data%'", Integer.class)).isZero();
    }

    @Test
    void elUsuarioDeE2ePuedeEntrarImportarUnaOfertaYAnalizarlaCon85() throws Exception {
        String token = login();

        String offer = mockMvc.perform(post("/api/v1/offers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawText\":\"" + TestRows.VALID_RAW_TEXT + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String offerId = JsonPath.read(offer, "$.id");

        mockMvc.perform(post("/api/v1/offers/{id}/analysis", offerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(85));
    }

    /** Cada ejecución de la suite E2E gasta 2 llamadas de IA del mismo usuario: con 30/h, la 16.ª en una hora fallaría. */
    @Test
    void elCupoDeIaDelPerfilE2eNoSeAgotaAlRepetirLaSuite() throws Exception {
        String token = login();
        String offer = mockMvc.perform(post("/api/v1/offers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawText\":\"" + TestRows.VALID_RAW_TEXT + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String offerId = JsonPath.read(offer, "$.id");

        for (int i = 0; i < 40; i++) {
            mockMvc.perform(post("/api/v1/offers/{id}/analysis", offerId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isCreated());
        }
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + E2E_EMAIL + "\",\"password\":\"" + E2E_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class, E2E_EMAIL);
        return value == null ? 0 : value;
    }
}
