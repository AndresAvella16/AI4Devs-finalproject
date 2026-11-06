package com.jobpilot.ai;

import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.jobpilot.shared.error.LlmOverloadedException;
import com.jobpilot.shared.error.LlmTimeoutException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeoutException;

/**
 * Traduce los errores del SDK de Anthropic (lanzados tal cual o envueltos por Spring AI) a las excepciones del
 * dominio. Busca en toda la cadena de causas.
 *
 * <ul>
 *   <li>Timeout de red → {@link LlmTimeoutException} (504).</li>
 *   <li>HTTP 429, 529 u otro 5xx, o proveedor no accesible → {@link LlmOverloadedException} (503), con el
 *       {@code Retry-After} del proveedor si lo envía en segundos.</li>
 *   <li>Cualquier otro (p. ej. 401 por una API key mala): es un error de configuración y se devuelve tal cual,
 *       para que acabe como 500 con la traza en el log.</li>
 * </ul>
 *
 * <p>Los mensajes nuevos nunca copian el cuerpo de la respuesta del proveedor.
 */
class ProviderErrorTranslator {

    private static final int MAX_CAUSE_DEPTH = 20;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int FIRST_SERVER_ERROR = 500;

    private final long defaultRetryAfterSeconds;

    ProviderErrorTranslator(long defaultRetryAfterSeconds) {
        if (defaultRetryAfterSeconds <= 0) {
            throw new IllegalArgumentException("defaultRetryAfterSeconds debe ser positivo: " + defaultRetryAfterSeconds);
        }
        this.defaultRetryAfterSeconds = defaultRetryAfterSeconds;
    }

    RuntimeException translate(RuntimeException error) {
        if (findCause(error, InterruptedIOException.class) != null || findCause(error, TimeoutException.class) != null) {
            return new LlmTimeoutException("El proveedor del LLM no ha respondido a tiempo", error);
        }
        AnthropicServiceException service = findCause(error, AnthropicServiceException.class);
        if (service != null) {
            int status = service.statusCode();
            if (status == TOO_MANY_REQUESTS || status >= FIRST_SERVER_ERROR) {
                return new LlmOverloadedException(retryAfter(service.headers()),
                        "Proveedor del LLM saturado o con errores (HTTP " + status + ")", error);
            }
            return error;
        }
        if (findCause(error, AnthropicIoException.class) != null) {
            return new LlmOverloadedException(defaultRetryAfterSeconds, "Proveedor del LLM no accesible", error);
        }
        return error;
    }

    /** {@code Retry-After} en segundos; si falta, no es un número o no es positivo, el valor por defecto. */
    private long retryAfter(Headers headers) {
        for (String value : headers.values("retry-after")) {
            try {
                long seconds = Long.parseLong(value.strip());
                if (seconds > 0) {
                    return seconds;
                }
            } catch (NumberFormatException ignored) {
                // Formato de fecha HTTP u otro valor: se usa el valor por defecto.
            }
        }
        return defaultRetryAfterSeconds;
    }

    private static <E extends Throwable> E findCause(Throwable error, Class<E> type) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
