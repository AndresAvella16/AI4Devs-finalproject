package com.jobpilot.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/auth/login} (schema {@code LoginRequest} de la sección 4). La contraseña no pasa
 * la política de registro: una que no la cumpla es simplemente incorrecta (401), no un error de formato.
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank String password) {
}
