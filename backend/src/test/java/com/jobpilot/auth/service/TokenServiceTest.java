package com.jobpilot.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.shared.security.JwtConfig;
import com.jobpilot.shared.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Emisión con el JwtEncoder y validación con el JwtDecoder que usa el resource server (HS256, issuer, Clock). */
class TokenServiceTest {

    private static final String SECRET = "test-only-secreto-de-al-menos-32-bytes!!";
    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-0000-4000-8000-000000000001");

    private final SecretKey key = JwtConfig.secretKey(SECRET);
    private final TokenService tokens = new TokenService(JwtConfig.encoder(key), clockAt(NOW), properties(60));

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static SecurityProperties properties(long expirationMinutes) {
        return new SecurityProperties(new SecurityProperties.Jwt(SECRET, expirationMinutes),
                new SecurityProperties.Cors(List.of("http://localhost:5173")));
    }

    private JwtDecoder decoderAt(Instant instant) {
        return JwtConfig.decoder(key, clockAt(instant));
    }

    private static String sign(SecretKey key, MacAlgorithm algorithm, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(algorithm).type("JWT").build();
        // Encoder propio del test: puede firmar con un algoritmo distinto del de la app (p. ej. HS512).
        return NimbusJwtEncoder.withSecretKey(key).algorithm(algorithm).build()
                .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static JwtClaimsSet.Builder validClaims() {
        return JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(USER_ID.toString())
                .issuedAt(NOW)
                .expiresAt(NOW.plus(Duration.ofMinutes(60)));
    }

    @Test
    void emiteUnTokenHs256ConSubjectIssuerYFechasDelClock() {
        IssuedToken token = tokens.issue(USER_ID);

        Jwt jwt = decoderAt(NOW).decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("jobpilot");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat(token.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void laCaducidadSaleDeLaConfiguracion() {
        TokenService shortLived = new TokenService(JwtConfig.encoder(key), clockAt(NOW), properties(5));

        IssuedToken token = shortLived.issue(USER_ID);

        assertThat(token.expiresInSeconds()).isEqualTo(300);
        assertThat(decoderAt(NOW).decode(token.value()).getExpiresAt()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void elTokenEsValidoHastaSuCaducidadYNoDespues() {
        String token = tokens.issue(USER_ID).value();

        assertThatCode(() -> decoderAt(NOW.plus(Duration.ofMinutes(59))).decode(token)).doesNotThrowAnyException();
        assertThatThrownBy(() -> decoderAt(NOW.plus(Duration.ofMinutes(61))).decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rechazaUnTokenConLaFirmaAlterada() {
        String token = tokens.issue(USER_ID).value();
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> decoderAt(NOW).decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnTokenConElPayloadAlteradoManteniendoLaFirma() {
        String[] parts = tokens.issue(USER_ID).value().split("\\.");
        String otherPayload = base64Url("{\"sub\":\"" + UUID.randomUUID() + "\",\"iss\":\"jobpilot\","
                + "\"iat\":" + NOW.getEpochSecond() + ",\"exp\":" + NOW.plusSeconds(3600).getEpochSecond() + "}");

        assertThatThrownBy(() -> decoderAt(NOW).decode(parts[0] + "." + otherPayload + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnTokenFirmadoConOtroSecreto() {
        SecretKey otherKey = JwtConfig.secretKey("test-only-otro-secreto-distinto-de-32-bytes");
        String token = sign(otherKey, MacAlgorithm.HS256, validClaims().build());

        assertThatThrownBy(() -> decoderAt(NOW).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnTokenSinFirmaConAlgNone() {
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + USER_ID + "\",\"iss\":\"jobpilot\",\"iat\":"
                + NOW.getEpochSecond() + ",\"exp\":" + NOW.plusSeconds(3600).getEpochSecond() + "}");

        assertThatThrownBy(() -> decoderAt(NOW).decode(header + "." + payload + "."))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnTokenFirmadoConOtroAlgoritmoAunqueSeaElMismoSecreto() {
        String longSecret = "test-only-secreto-largo-para-hs512-de-al-menos-64-bytes-xxxxxxxxx";
        SecretKey longKey = JwtConfig.secretKey(longSecret);
        String hs512 = sign(longKey, MacAlgorithm.HS512, validClaims().build());

        assertThatThrownBy(() -> JwtConfig.decoder(longKey, clockAt(NOW)).decode(hs512))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnTokenDeOtroEmisor() {
        String token = sign(key, MacAlgorithm.HS256, validClaims().issuer("otro-emisor").build());

        assertThatThrownBy(() -> decoderAt(NOW).decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("iss");
    }

    @Test
    void rechazaUnTokenSinFechaDeCaducidad() {
        JwtClaimsSet noExpiry = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER).subject(USER_ID.toString()).issuedAt(NOW).build();
        String token = sign(key, MacAlgorithm.HS256, noExpiry);

        assertThatThrownBy(() -> decoderAt(NOW).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnSecretoDeMenosDe32Bytes() {
        assertThatThrownBy(() -> JwtConfig.secretKey("corto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("32");
        assertThatThrownBy(() -> JwtConfig.secretKey(null)).isInstanceOf(IllegalStateException.class);
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
