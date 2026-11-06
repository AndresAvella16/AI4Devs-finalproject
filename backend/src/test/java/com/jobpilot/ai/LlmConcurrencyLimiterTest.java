package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.shared.error.LlmOverloadedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** CA 7: como máximo N llamadas simultáneas al LLM; si no hay hueco, 503 inmediato con Retry-After. */
class LlmConcurrencyLimiterTest {

    private final LlmConcurrencyLimiter limiter = new LlmConcurrencyLimiter(3, 10);

    private final ExecutorService threads = Executors.newCachedThreadPool();

    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void stopThreads() {
        release.countDown();
        threads.shutdownNow();
    }

    private void occupy(int calls) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(calls);
        for (int i = 0; i < calls; i++) {
            threads.submit(() -> limiter.run(() -> {
                started.countDown();
                awaitRelease();
                return "ok";
            }));
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private void awaitRelease() {
        try {
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void conTresHuecosOcupadosLaCuartaLlamadaFallaAlMomentoSinEjecutarse() throws Exception {
        occupy(3);
        AtomicBoolean executed = new AtomicBoolean();

        assertThatThrownBy(() -> limiter.run(() -> executed.getAndSet(true)))
                .isInstanceOfSatisfying(LlmOverloadedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(10));
        assertThat(executed).isFalse();
    }

    @Test
    void conDosHuecosOcupadosLaTerceraSeEjecuta() throws Exception {
        occupy(2);

        assertThat(limiter.run(() -> "tercera")).isEqualTo("tercera");
    }

    @Test
    void elHuecoSeLiberaAlTerminarBien() {
        for (int i = 0; i < 10; i++) {
            limiter.run(() -> "ok");
        }

        assertThat(limiter.availableSlots()).isEqualTo(3);
    }

    @Test
    void elHuecoSeLiberaAunqueLaLlamadaLanceUnaExcepcion() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> limiter.run(() -> {
                throw new IllegalStateException("fallo del LLM");
            })).isInstanceOf(IllegalStateException.class);
        }

        assertThat(limiter.availableSlots()).isEqualTo(3);
    }

    @Test
    void rechazaUnaConfiguracionSinHuecosOConRetryAfterNoPositivo() {
        assertThatThrownBy(() -> new LlmConcurrencyLimiter(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LlmConcurrencyLimiter(3, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
