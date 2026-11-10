package com.jobpilot.profile.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Borradores del CV en memoria, uno por usuario (JP-BE-04). {@code POST /profile/cv} guarda aquí el texto extraído
 * y el nombre del fichero sin tocar la BD, porque el perfil no se guarda hasta que el usuario lo confirma;
 * {@code PUT /profile} los añade al perfil y olvida el borrador. Si caduca antes (30 min), el perfil se guarda sin
 * ellos.
 *
 * <p>Como los cubos de Bucket4j, vale para una sola instancia y se pierde al reiniciar: lo peor que pasa es que el
 * perfil se guarde sin el texto del CV. La memoria está acotada: al llegar a {@code maxEntries} se olvidan primero
 * los caducados y después los que antes caducan.
 */
@Component
@EnableConfigurationProperties(CvDraftProperties.class)
class CvDraftStore {

    private final Map<UUID, CvDraft> drafts = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;

    @Autowired
    CvDraftStore(CvDraftProperties properties, Clock clock) {
        this(properties.ttl(), properties.maxEntries(), clock);
    }

    CvDraftStore(Duration ttl, int maxEntries, Clock clock) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl debe ser positivo: " + ttl);
        }
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Guarda el borrador del usuario; sustituye al que tuviera. */
    CvDraft put(UUID userId, String fileName, String rawText) {
        CvDraft draft = new CvDraft(fileName, rawText, clock.instant().plus(ttl));
        if (!drafts.containsKey(userId) && drafts.size() >= maxEntries) {
            makeRoom();
        }
        drafts.put(userId, draft);
        return draft;
    }

    /** El borrador del usuario, si tiene uno sin caducar. */
    Optional<CvDraft> find(UUID userId) {
        CvDraft draft = drafts.get(userId);
        if (draft == null) {
            return Optional.empty();
        }
        if (isExpired(draft, clock.instant())) {
            drafts.remove(userId, draft);
            return Optional.empty();
        }
        return Optional.of(draft);
    }

    /** Olvida el borrador solo si sigue siendo ese: si el usuario ha subido otro CV mientras tanto, se conserva. */
    void remove(UUID userId, CvDraft draft) {
        drafts.remove(userId, draft);
    }

    int size() {
        return drafts.size();
    }

    private synchronized void makeRoom() {
        Instant now = clock.instant();
        drafts.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        while (drafts.size() >= maxEntries) {
            drafts.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                    .ifPresent(oldest -> drafts.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    private static boolean isExpired(CvDraft draft, Instant now) {
        return !now.isBefore(draft.expiresAt());
    }
}
