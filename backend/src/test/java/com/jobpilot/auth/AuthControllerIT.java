package com.jobpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.support.PostgresTestcontainer;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** CA 1–5: registro y login de punta a punta (API → servicio → BD). Cada test usa un email nuevo. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class AuthControllerIT {

    private static final String PROBLEMS = "https://jobpilot.dev/problems/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtDecoder jwtDecoder;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.dev";
    }

    private static String json(String email, String password, String fullName) {
        StringBuilder body = new StringBuilder("{");
        if (email != null) {
            body.append("\"email\":\"").append(email).append("\",");
        }
        if (password != null) {
            body.append("\"password\":\"").append(password).append("\",");
        }
        if (fullName != null) {
            body.append("\"fullName\":\"").append(fullName).append("\",");
        }
        if (body.length() > 1) {
            body.setLength(body.length() - 1);
        }
        return body.append("}").toString();
    }

    private ResultActions register(String email, String password, String fullName) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(email, password, fullName)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(email, password, null)));
    }

    // ---- Registro ----

    @Test
    void registroValidoDevuelve201ConTokenYGuardaElUsuarioNormalizado() throws Exception {
        String email = uniqueEmail();
        String mixedCase = email.toUpperCase();

        String response = register(mixedCase, "Demo1234!", "Ana García")
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.fullName").value("Ana García"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("$2a$"))))
                .andReturn().getResponse().getContentAsString();

        var row = jdbc.queryForMap("SELECT id, email, password_hash FROM users WHERE email = ?", email);
        assertThat((String) row.get("password_hash")).matches("^\\$2[aby]\\$12\\$.{53}$");
        assertThat(response).contains(row.get("id").toString());

        String token = com.jayway.jsonpath.JsonPath.read(response, "$.accessToken");
        assertThat(jwtDecoder.decode(token).getSubject()).isEqualTo(row.get("id").toString());
    }

    @Test
    void registrarUnEmailYaRegistradoSinDistinguirMayusculasDevuelve409() throws Exception {
        String email = uniqueEmail();
        register(email, "Demo1234!", "Ana").andExpect(status().isCreated());

        register(email.toUpperCase(), "Otra5678!", "Otra Ana")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "email-already-registered"))
                .andExpect(jsonPath("$.title").value("Email ya registrado"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/register"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
                .isOne();
    }

    static Stream<Arguments> invalidRegistrations() {
        String valid = "Demo1234!";
        return Stream.of(
                Arguments.of("email mal formado", "no-es-un-email", valid, "Ana", "email"),
                Arguments.of("email ausente", null, valid, "Ana", "email"),
                Arguments.of("contraseña corta", uniqueEmail(), "abc123", "Ana", "password"),
                Arguments.of("contraseña sin números", uniqueEmail(), "abcdefgh", "Ana", "password"),
                Arguments.of("contraseña sin letras", uniqueEmail(), "12345678", "Ana", "password"),
                Arguments.of("contraseña de más de 72 bytes", uniqueEmail(), "ñ".repeat(36) + "1", "Ana", "password"),
                Arguments.of("contraseña ausente", uniqueEmail(), null, "Ana", "password"),
                Arguments.of("nombre vacío", uniqueEmail(), valid, "   ", "fullName"),
                Arguments.of("nombre de 121 caracteres", uniqueEmail(), valid, "a".repeat(121), "fullName"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrations")
    void registroConDatosNoValidosDevuelve400ConElCampo(String caseName, String email, String password,
            String fullName, String field) throws Exception {
        register(email, password, fullName)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-error"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    // ---- Login ----

    @Test
    void loginCorrectoSinDistinguirMayusculasDevuelve200ConToken() throws Exception {
        String email = uniqueEmail();
        register(email, "Demo1234!", "Ana").andExpect(status().isCreated());

        login(email.toUpperCase(), "Demo1234!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void loginConContrasenaIncorrectaYConEmailInexistenteDanLaMismaRespuesta401() throws Exception {
        String email = uniqueEmail();
        register(email, "Demo1234!", "Ana").andExpect(status().isCreated());

        String wrongPassword = login(email, "Otra5678!")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-credentials"))
                .andExpect(jsonPath("$.title").value("Credenciales incorrectas"))
                .andExpect(jsonPath("$.status").value(401))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = login(uniqueEmail(), "Otra5678!")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void loginConDatosNoValidosDevuelve400() throws Exception {
        login("no-es-un-email", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-error"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));
    }
}
