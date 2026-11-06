package com.jobpilot.shared.security;

import java.util.Objects;
import java.util.UUID;

/**
 * Usuario autenticado: el principal que reciben los controllers con {@code @AuthenticationPrincipal}.
 * Su {@code userId} sale del {@code sub} del JWT y es el que filtra todas las consultas ({@code findByIdAndUserId}).
 */
public record AuthenticatedUser(UUID userId) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId");
    }
}
