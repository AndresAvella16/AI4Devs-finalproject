package com.jobpilot.shared.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.auth.service.TokenService;
import com.jobpilot.support.PostgresTestcontainer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** CA 7–10: filtro JWT del resource server, 401 como ProblemDetail, rutas públicas y CORS. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, SecurityIT.WhoAmIController.class})
class SecurityIT {

    private static final String PROTECTED = "/api/v1/test/whoami";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokens;

    @Autowired
    private JwtEncoder jwtEncoder;

    /** Endpoint protegido que solo existe en este test: devuelve el principal que ha fijado el filtro JWT. */
    @RestController
    static class WhoAmIController {

        @GetMapping(PROTECTED)
        Map<String, String> whoami(@AuthenticationPrincipal AuthenticatedUser user) {
            return Map.of("userId", user.userId().toString());
        }
    }

    private ResultActions getProtected(String authorization) throws Exception {
        var request = get(PROTECTED);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private static ResultActions expectUnauthorizedProblem(ResultActions result) throws Exception {
        return result
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://jobpilot.dev/problems/unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value(PROTECTED));
    }

    private String signed(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // ---- Con token válido ----

    @Test
    void conUnTokenValidoElEndpointRecibeElUserIdComoPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();

        getProtected("Bearer " + tokens.issue(userId).value())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    // ---- Sin token o con un token no válido → 401 ProblemDetail ----

    @Test
    void sinTokenDevuelve401() throws Exception {
        expectUnauthorizedProblem(getProtected(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer no-es-un-jwt", "Bearer a.b.c", "Basic dXNlcjpwYXNz", "Bearer "})
    void conUnaCabeceraNoValidaDevuelve401(String authorization) throws Exception {
        expectUnauthorizedProblem(getProtected(authorization));
    }

    @Test
    void conUnTokenCaducadoDevuelve401() throws Exception {
        Instant past = Instant.now().minusSeconds(7200);
        String expired = signed(JwtClaimsSet.builder().issuer("jobpilot").subject(UUID.randomUUID().toString())
                .issuedAt(past).expiresAt(past.plusSeconds(3600)).build());

        expectUnauthorizedProblem(getProtected("Bearer " + expired));
    }

    @Test
    void conUnTokenFirmadoConOtroSecretoDevuelve401() throws Exception {
        var otherEncoder = JwtConfig.encoder(JwtConfig.secretKey("test-only-otro-secreto-distinto-de-32-bytes"));
        Instant now = Instant.now();
        String forged = otherEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                JwtClaimsSet.builder().issuer("jobpilot").subject(UUID.randomUUID().toString())
                        .issuedAt(now).expiresAt(now.plusSeconds(3600)).build())).getTokenValue();

        expectUnauthorizedProblem(getProtected("Bearer " + forged));
    }

    @Test
    void conUnTokenCuyoSubjectNoEsUnUuidDevuelve401() throws Exception {
        Instant now = Instant.now();
        String token = signed(JwtClaimsSet.builder().issuer("jobpilot").subject("demo@jobpilot.dev")
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).build());

        expectUnauthorizedProblem(getProtected("Bearer " + token));
    }

    @Test
    void unaRutaInexistenteDeLaApiExigeTokenAntesDeDevolver404() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/no-existe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(UUID.randomUUID()).value()))
                .andExpect(status().isNotFound());
    }

    // ---- Rutas públicas ----

    @Test
    void loginYRegistroSonPublicos() throws Exception {
        // Sin token: llega a la validación del controller (400), no se queda en el filtro (401).
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthYLaDocumentacionDeLaApiSonPublicos() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void unTokenNoValidoEnUnaRutaPublicaSeIgnoraYNoBloquea() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, "Bearer basura"))
                .andExpect(status().isOk());
    }

    // ---- CORS ----

    @Test
    void elPreflightDesdeElOrigenDelFrontendSePermite() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")));
    }

    @Test
    void elPreflightDesdeOtroOrigenSeRechaza() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void lasRespuestasCorsExponenLocationYRetryAfter() throws Exception {
        mockMvc.perform(get(PROTECTED)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(UUID.randomUUID()).value()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Location")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")));
    }
}
