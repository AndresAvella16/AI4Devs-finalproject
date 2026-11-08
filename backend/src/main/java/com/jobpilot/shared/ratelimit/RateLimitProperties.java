package com.jobpilot.shared.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límites de peticiones ({@code jobpilot.rate-limit.*}, sección 2.5).
 *
 * @param ai      llamadas a endpoints de IA por usuario (30 por hora)
 * @param login   intentos de login por IP + email (10 por minuto)
 * @param maxKeys claves que se recuerdan por límite antes de olvidar las que tienen el cupo lleno
 */
@Validated
@ConfigurationProperties(prefix = "jobpilot.rate-limit")
public record RateLimitProperties(@Valid @NotNull Limit ai, @Valid @NotNull Limit login, @Positive int maxKeys) {

    public record Limit(@Positive int capacity, @NotNull Duration period) {
    }
}
