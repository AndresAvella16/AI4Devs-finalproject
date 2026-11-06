package com.jobpilot.auth.service;

import com.jobpilot.shared.security.JwtConfig;
import com.jobpilot.shared.security.SecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emite el JWT de acceso: {@code sub} = userId, {@code iss} = jobpilot, {@code iat}/{@code exp} con el Clock. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration validity;

    public TokenService(JwtEncoder encoder, Clock clock, SecurityProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.validity = Duration.ofMinutes(properties.jwt().expirationMinutes());
    }

    public IssuedToken issue(UUID userId) {
        // Las fechas de un JWT van en segundos: se trunca para que iat/exp sean exactamente las emitidas.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(validity))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, validity.toSeconds());
    }
}
