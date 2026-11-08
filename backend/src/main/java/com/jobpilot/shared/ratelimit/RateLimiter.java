package com.jobpilot.shared.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Un cubo de Bucket4j por clave (usuario, IP + email...), en memoria. {@code capacity} peticiones por
 * {@code period}, con recarga progresiva: 30 por hora devuelve una petición cada 2 minutos, en lugar de las 30
 * de golpe al cumplirse la hora.
 *
 * <p>Para que el mapa no crezca sin límite (cada email que se prueba en el login es una clave), al llegar a
 * {@code maxKeys} se olvidan las claves con el cubo lleno: para ellas, olvidarlas y crearlas de nuevo es lo mismo.
 * Las que siguen limitadas se conservan siempre.
 *
 * <p>Solo vale para una instancia del backend (el despliegue previsto; ver la deuda técnica en el readme).
 */
public final class RateLimiter {

    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final Bandwidth bandwidth;
    private final long capacity;
    private final TimeMeter time;
    private final int maxKeys;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int capacity, Duration period, TimeMeter time, int maxKeys) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity debe ser al menos 1: " + capacity);
        }
        if (period == null || period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("period debe ser positivo: " + period);
        }
        if (maxKeys < 1) {
            throw new IllegalArgumentException("maxKeys debe ser al menos 1: " + maxKeys);
        }
        this.bandwidth = Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build();
        this.capacity = capacity;
        this.time = Objects.requireNonNull(time, "time");
        this.maxKeys = maxKeys;
    }

    /**
     * Gasta una petición de {@code key}.
     *
     * @return {@code 0} si se permite; si no, los segundos (redondeados hacia arriba, al menos 1) que faltan para
     *         que haya hueco, para la cabecera {@code Retry-After}
     */
    public long tryConsume(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            evictFullBucketsIfNeeded();
            bucket = buckets.computeIfAbsent(key, k -> newBucket());
        }
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return 0;
        }
        long nanos = probe.getNanosToWaitForRefill();
        return Math.max(1, (nanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND);
    }

    int trackedKeys() {
        return buckets.size();
    }

    private Bucket newBucket() {
        return Bucket.builder().addLimit(bandwidth).withCustomTimePrecision(time).build();
    }

    private void evictFullBucketsIfNeeded() {
        if (buckets.size() >= maxKeys) {
            buckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= capacity);
        }
    }
}
