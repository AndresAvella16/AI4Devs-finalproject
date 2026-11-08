package com.jobpilot.shared.error;

/**
 * Límite de peticiones superado (→ 429 {@code rate-limit-exceeded} con la cabecera {@code Retry-After}, sección 2.5).
 * El mensaje es el {@code detail} de la respuesta: no debe incluir datos del usuario.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds, String detail) {
        super(detail);
        if (retryAfterSeconds <= 0) {
            throw new IllegalArgumentException("retryAfterSeconds debe ser positivo: " + retryAfterSeconds);
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
