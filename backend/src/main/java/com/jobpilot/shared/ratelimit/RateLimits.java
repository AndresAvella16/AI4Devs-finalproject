package com.jobpilot.shared.ratelimit;

import com.jobpilot.shared.error.RateLimitExceededException;
import io.github.bucket4j.TimeMeter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Los límites de uso de la aplicación (sección 2.5), con un punto de entrada para cada uno. Si se supera, lanza
 * {@link RateLimitExceededException} (→ 429 con {@code Retry-After}).
 *
 * <ul>
 *   <li>{@link #consumeAiCall(UUID)}: lo llama cada servicio justo antes de usar el LLM, después de comprobar todo
 *       lo demás (404, 409...), para que una petición que no llega a la IA no gaste cupo. Lo comparten todos los
 *       endpoints de IA.</li>
 *   <li>{@link #consumeLoginAttempt(String, String)}: lo llama el login antes de comprobar la contraseña. Cuentan
 *       todos los intentos, correctos o no.</li>
 * </ul>
 *
 * <p>El límite global de llamadas simultáneas al LLM (503) no está aquí: lo aplica el módulo {@code ai}.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimits {

    private final RateLimiter ai;
    private final RateLimiter login;

    public RateLimits(RateLimitProperties properties) {
        this.ai = limiter(properties.ai(), properties.maxKeys());
        this.login = limiter(properties.login(), properties.maxKeys());
    }

    private static RateLimiter limiter(RateLimitProperties.Limit limit, int maxKeys) {
        return new RateLimiter(limit.capacity(), limit.period(), TimeMeter.SYSTEM_NANOTIME, maxKeys);
    }

    public void consumeAiCall(UUID userId) {
        long retryAfter = ai.tryConsume(userId.toString());
        if (retryAfter > 0) {
            throw new RateLimitExceededException(retryAfter, "Has superado el límite de peticiones a la IA. "
                    + "Inténtalo de nuevo en " + retryAfter + " segundos.");
        }
    }

    /** {@code email} se normaliza igual que en el login, para que cambiar las mayúsculas no dé intentos nuevos. */
    public void consumeLoginAttempt(String clientIp, String email) {
        String key = clientIp + "|" + email.strip().toLowerCase(Locale.ROOT);
        long retryAfter = login.tryConsume(key);
        if (retryAfter > 0) {
            throw new RateLimitExceededException(retryAfter, "Demasiados intentos de inicio de sesión. "
                    + "Inténtalo de nuevo en " + retryAfter + " segundos.");
        }
    }
}
