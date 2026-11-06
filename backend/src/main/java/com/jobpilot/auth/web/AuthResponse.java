package com.jobpilot.auth.web;

import com.jobpilot.auth.service.AuthResult;

/** Respuesta de registro y login (schema {@code AuthResponse} de la sección 4). {@code expiresIn} en segundos. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {

    static AuthResponse from(AuthResult result) {
        return new AuthResponse(result.token().value(), "Bearer", result.token().expiresInSeconds(),
                UserSummary.from(result.user()));
    }
}
