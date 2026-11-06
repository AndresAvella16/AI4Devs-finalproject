package com.jobpilot.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class AuthenticatedUserConverterTest {

    private final AuthenticatedUserConverter converter = new AuthenticatedUserConverter();

    private static Jwt jwtWithSubject(String subject) {
        Instant now = Instant.parse("2026-11-06T10:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }

    @Test
    void convierteElSubjectEnUnAuthenticatedUserAutenticado() {
        UUID userId = UUID.randomUUID();

        AbstractAuthenticationToken authentication = converter.convert(jwtWithSubject(userId.toString()));

        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUser(userId));
        assertThat(authentication.getName()).isEqualTo(userId.toString());
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void rechazaUnSubjectQueNoEsUnUuid() {
        assertThatThrownBy(() -> converter.convert(jwtWithSubject("demo@jobpilot.dev")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }
}
