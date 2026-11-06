package com.jobpilot.shared.error;

/**
 * No hay hueco para más llamadas simultáneas al LLM o el proveedor está saturado
 * (→ 503 con la cabecera {@code Retry-After}).
 */
public class LlmOverloadedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LlmOverloadedException(long retryAfterSeconds) {
        this(retryAfterSeconds, "LLM saturado; reintentar en " + retryAfterSeconds + " s", null);
    }

    public LlmOverloadedException(long retryAfterSeconds, String message, Throwable cause) {
        super(message, cause);
        if (retryAfterSeconds <= 0) {
            throw new IllegalArgumentException("retryAfterSeconds debe ser positivo: " + retryAfterSeconds);
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
