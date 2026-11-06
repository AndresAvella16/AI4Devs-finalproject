package com.jobpilot.shared.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de seguridad ({@code jobpilot.security.*}), leída de JWT_SECRET, JWT_EXPIRATION_MINUTES y
 * CORS_ALLOWED_ORIGINS. La longitud mínima del secreto la comprueba {@link JwtConfig#secretKey(String)}.
 */
@Validated
@ConfigurationProperties(prefix = "jobpilot.security")
public record SecurityProperties(@Valid Jwt jwt, @Valid Cors cors) {

    public record Jwt(@NotBlank String secret, @Positive long expirationMinutes) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }
}
