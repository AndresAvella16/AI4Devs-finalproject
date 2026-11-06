package com.jobpilot.shared.security;

import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Convierte el JWT ya validado en la autenticación de la petición, con un {@link AuthenticatedUser} como principal.
 * Un {@code sub} que no es un UUID se trata como token no válido (→ 401).
 */
public class AuthenticatedUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new AuthenticatedUserToken(new AuthenticatedUser(userId(jwt)), jwt);
    }

    private static UUID userId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new InvalidBearerTokenException("El token no identifica a ningún usuario");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("El token no identifica a ningún usuario");
        }
    }

    /** Autenticación sin roles: en el MVP todos los usuarios tienen los mismos permisos sobre sus propios datos. */
    private static final class AuthenticatedUserToken extends AbstractAuthenticationToken {

        private final AuthenticatedUser principal;
        private final Jwt jwt;

        AuthenticatedUserToken(AuthenticatedUser principal, Jwt jwt) {
            super(List.of());
            this.principal = principal;
            this.jwt = jwt;
            setAuthenticated(true);
        }

        @Override
        public AuthenticatedUser getPrincipal() {
            return principal;
        }

        @Override
        public Jwt getCredentials() {
            return jwt;
        }

        @Override
        public String getName() {
            return principal.userId().toString();
        }
    }
}
