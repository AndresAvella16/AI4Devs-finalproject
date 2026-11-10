package com.jobpilot.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Borrador del CV en memoria (decisión de JP-BE-04): {@code POST /profile/cv} guarda aquí el texto extraído y
 * {@code PUT /profile} lo añade al perfil. Caduca a los 30 minutos y la memoria está acotada.
 */
class CvDraftStoreTest {

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);

    private final MutableClock clock = new MutableClock(T0);
    private final CvDraftStore store = new CvDraftStore(TTL, 3, clock);
    private final UUID user = UUID.randomUUID();

    @Test
    void guardaElBorradorDelUsuarioHastaQueCaduca() {
        CvDraft draft = store.put(user, "cv.pdf", "Texto del CV");

        assertThat(draft.fileName()).isEqualTo("cv.pdf");
        assertThat(draft.rawText()).isEqualTo("Texto del CV");
        assertThat(draft.expiresAt()).isEqualTo(T0.plus(TTL));

        clock.advance(TTL.minusSeconds(1));
        assertThat(store.find(user)).contains(draft);
    }

    @Test
    void alCaducarYaNoSeEncuentraYSeOlvida() {
        store.put(user, "cv.pdf", "Texto del CV");

        clock.advance(TTL);

        assertThat(store.find(user)).isEmpty();
        assertThat(store.size()).isZero();
    }

    @Test
    void cadaUsuarioTieneSuPropioBorrador() {
        UUID other = UUID.randomUUID();
        store.put(user, "mio.pdf", "Mío");

        assertThat(store.find(other)).isEmpty();
        assertThat(store.find(user)).get().extracting(CvDraft::fileName).isEqualTo("mio.pdf");
    }

    @Test
    void unaSubidaNuevaSustituyeALaAnteriorYRenuevaLaCaducidad() {
        store.put(user, "viejo.pdf", "Viejo");
        clock.advance(Duration.ofMinutes(20));

        CvDraft newer = store.put(user, "nuevo.pdf", "Nuevo");
        clock.advance(Duration.ofMinutes(20));

        assertThat(store.find(user)).contains(newer);
    }

    @Test
    void removeSoloBorraElBorradorQueSeHaUsado() {
        CvDraft used = store.put(user, "viejo.pdf", "Viejo");
        CvDraft newer = store.put(user, "nuevo.pdf", "Nuevo");

        store.remove(user, used);
        assertThat(store.find(user)).contains(newer);

        store.remove(user, newer);
        assertThat(store.find(user)).isEmpty();
    }

    @Test
    void alLlenarseOlvidaPrimeroLosCaducados() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        store.put(a, "a.pdf", "A");
        clock.advance(Duration.ofMinutes(31));
        store.put(b, "b.pdf", "B");
        store.put(c, "c.pdf", "C");

        store.put(user, "u.pdf", "U");

        assertThat(store.size()).isEqualTo(3);
        assertThat(store.find(b)).isPresent();
        assertThat(store.find(c)).isPresent();
        assertThat(store.find(user)).isPresent();
    }

    @Test
    void alLlenarseSinCaducadosOlvidaElQueAntesCaduca() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        store.put(a, "a.pdf", "A");
        clock.advance(Duration.ofMinutes(1));
        store.put(b, "b.pdf", "B");
        clock.advance(Duration.ofMinutes(1));
        store.put(c, "c.pdf", "C");

        store.put(user, "u.pdf", "U");

        assertThat(store.size()).isEqualTo(3);
        assertThat(store.find(a)).isEmpty();
        assertThat(store.find(user)).isPresent();
    }

    /** Reloj que avanza a mano. */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
