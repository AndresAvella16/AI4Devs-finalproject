package com.jobpilot.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Límite por clave con Bucket4j (sección 2.5), con un reloj simulado. */
class RateLimiterTest {

    /** Reloj de Bucket4j que solo avanza cuando el test lo pide. */
    static final class FakeTime implements TimeMeter {

        private final AtomicLong nanos = new AtomicLong();

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }

        @Override
        public long currentTimeNanos() {
            return nanos.get();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }

    private final FakeTime time = new FakeTime();

    private RateLimiter limiter(int capacity, Duration period) {
        return new RateLimiter(capacity, period, time, 1_000);
    }

    @Test
    void permiteHastaLaCapacidadYDespuesIndicaCuantoEsperar() {
        RateLimiter limiter = limiter(3, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume("a")).isZero();
        assertThat(limiter.tryConsume("a")).isZero();
        assertThat(limiter.tryConsume("a")).isZero();

        // Recarga progresiva: 3 por minuto = 1 cada 20 s.
        assertThat(limiter.tryConsume("a")).isEqualTo(20);
    }

    @Test
    void treintaPorHoraRecuperaUnaPeticionCadaDosMinutos() {
        RateLimiter limiter = limiter(30, Duration.ofHours(1));
        for (int i = 0; i < 30; i++) {
            assertThat(limiter.tryConsume("user")).isZero();
        }

        assertThat(limiter.tryConsume("user")).isEqualTo(120);

        time.advance(Duration.ofSeconds(119));
        assertThat(limiter.tryConsume("user")).isEqualTo(1);
        time.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryConsume("user")).isZero();
        assertThat(limiter.tryConsume("user")).isPositive();
    }

    @Test
    void elRetryAfterSeRedondeaHaciaArribaYNuncaEsCero() {
        RateLimiter limiter = limiter(1, Duration.ofSeconds(10));
        limiter.tryConsume("a");

        time.advance(Duration.ofMillis(9_500));

        assertThat(limiter.tryConsume("a")).isEqualTo(1);
    }

    @Test
    void cadaClaveTieneSuPropioCupo() {
        RateLimiter limiter = limiter(1, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume("a")).isZero();
        assertThat(limiter.tryConsume("a")).isPositive();
        assertThat(limiter.tryConsume("b")).isZero();
    }

    @Test
    void lasClavesConElCupoLlenoSeOlvidanAlSuperarElMaximo() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1), time, 2);
        limiter.tryConsume("a");
        limiter.tryConsume("b");
        time.advance(Duration.ofMinutes(1));

        limiter.tryConsume("c");

        // "a" y "b" ya se habían recargado del todo: olvidarlas no cambia nada para sus usuarios.
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void noSeOlvidanLasClavesQueAunEstanLimitadas() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1), time, 2);
        limiter.tryConsume("a");
        limiter.tryConsume("b");

        limiter.tryConsume("c");

        assertThat(limiter.trackedKeys()).isEqualTo(3);
        assertThat(limiter.tryConsume("a")).isPositive();
    }

    @Test
    void rechazaConfiguracionesSinSentido() {
        assertThatThrownBy(() -> limiter(0, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
