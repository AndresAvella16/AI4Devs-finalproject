package com.jobpilot.auth.web;

import com.jobpilot.auth.domain.User;
import java.util.UUID;

/** Datos públicos del usuario autenticado. Nunca incluye la contraseña ni su hash. */
public record UserSummary(UUID id, String email, String fullName) {

    static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName());
    }
}
