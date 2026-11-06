package com.jobpilot.ai;

import com.jobpilot.shared.error.LlmOverloadedException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Límite global de llamadas simultáneas al LLM (sección 2.5). Si no hay hueco no se espera: se responde 503 al
 * momento, para no bloquear hilos del servidor mientras otros esperan al modelo.
 */
class LlmConcurrencyLimiter {

    private final Semaphore slots;
    private final int maxConcurrentCalls;
    private final long retryAfterSeconds;

    LlmConcurrencyLimiter(int maxConcurrentCalls, long retryAfterSeconds) {
        if (maxConcurrentCalls < 1) {
            throw new IllegalArgumentException("maxConcurrentCalls debe ser al menos 1: " + maxConcurrentCalls);
        }
        if (retryAfterSeconds <= 0) {
            throw new IllegalArgumentException("retryAfterSeconds debe ser positivo: " + retryAfterSeconds);
        }
        this.slots = new Semaphore(maxConcurrentCalls);
        this.maxConcurrentCalls = maxConcurrentCalls;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Ejecuta la llamada si hay hueco; el hueco se libera siempre al terminar, también si falla. */
    <T> T run(Supplier<T> call) {
        if (!slots.tryAcquire()) {
            throw new LlmOverloadedException(retryAfterSeconds,
                    "Sin hueco para llamar al LLM: " + maxConcurrentCalls + " llamadas en curso", null);
        }
        try {
            return call.get();
        } finally {
            slots.release();
        }
    }

    int availableSlots() {
        return slots.availablePermits();
    }
}
