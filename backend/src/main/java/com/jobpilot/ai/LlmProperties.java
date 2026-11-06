package com.jobpilot.ai;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración {@code llm.*} (application.yml).
 *
 * @param mode                       {@code fake} o {@code real}; lo valida {@link LlmModeEnvironmentPostProcessor}
 * @param maxConcurrentCalls         llamadas simultáneas al LLM como máximo (sección 2.5)
 * @param busyRetryAfterSeconds      {@code Retry-After} cuando no hay hueco en el semáforo
 * @param providerRetryAfterSeconds  {@code Retry-After} cuando el proveedor está saturado y no indica uno
 */
@Validated
@ConfigurationProperties("llm")
public record LlmProperties(
        String mode,
        @Positive int maxConcurrentCalls,
        @Positive long busyRetryAfterSeconds,
        @Positive long providerRetryAfterSeconds) {
}
