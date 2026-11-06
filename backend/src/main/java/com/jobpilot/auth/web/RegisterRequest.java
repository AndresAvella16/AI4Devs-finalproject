package com.jobpilot.auth.web;

import com.jobpilot.shared.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/auth/register} (schema {@code RegisterRequest} de la sección 4). */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @ValidPassword String password,
        @NotBlank @Size(max = 120) String fullName) {
}
