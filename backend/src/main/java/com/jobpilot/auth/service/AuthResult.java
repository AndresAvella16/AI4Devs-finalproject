package com.jobpilot.auth.service;

import com.jobpilot.auth.domain.User;

/** Resultado de registro o login: el usuario y su token. El controller lo convierte en {@code AuthResponse}. */
public record AuthResult(User user, IssuedToken token) {
}
