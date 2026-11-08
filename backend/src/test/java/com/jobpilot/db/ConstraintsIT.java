package com.jobpilot.db;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.support.PostgresTestcontainer;
import com.jobpilot.support.TestRows;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * CA 3 y 4: las restricciones de la sección 3.2 rechazan los datos no válidos. Cada caso comprueba el nombre de la
 * restricción violada, para no dar por bueno un fallo por otro motivo. Cada test se revierte al terminar.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
@Transactional
class ConstraintsIT {

    @Autowired
    private JdbcTemplate jdbc;

    private TestRows rows;

    @BeforeEach
    void setUp() {
        rows = new TestRows(jdbc);
    }

    private static void assertViolates(String constraint, ThrowingCallable insert) {
        assertThatThrownBy(insert)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @Nested
    class Users {

        @Test
        void rechazaUnEmailConMayusculas() {
            assertViolates("ck_users_email_lowercase", () -> rows.user("Demo@JobPilot.dev"));
        }

        @Test
        void rechazaUnEmailDuplicado() {
            rows.user("ana@test.dev");
            assertViolates("uq_users_email", () -> rows.user("ana@test.dev"));
        }

        @Test
        void aceptaUnEmailEnMinusculas() {
            assertThatCode(() -> rows.user("ana@test.dev")).doesNotThrowAnyException();
        }
    }

    @Nested
    class Profiles {

        @Test
        void rechazaUnSegundoPerfilParaElMismoUsuario() {
            UUID user = rows.user();
            rows.profile(user);
            assertViolates("uq_profiles_user_id", () -> rows.profile(user));
        }

        @Test
        void rechazaAniosDeExperienciaFueraDeRango() {
            UUID user = rows.user();
            assertViolates("ck_profiles_years_experience", () -> jdbc.update(
                    "INSERT INTO profiles (id, user_id, years_experience) VALUES (?, ?, 61)", UUID.randomUUID(), user));
        }

        @Test
        void rechazaIdiomasQueNoSonUnArrayJson() {
            UUID user = rows.user();
            assertViolates("ck_profiles_languages_array", () -> jdbc.update(
                    "INSERT INTO profiles (id, user_id, languages) VALUES (?, ?, '{\"language\":\"Inglés\"}'::jsonb)",
                    UUID.randomUUID(), user));
        }

        @Test
        void rechazaHabilidadesRepetidasSinDistinguirMayusculas() {
            UUID profile = rows.profile(rows.user());
            rows.skill(profile, "Spring Boot");
            assertViolates("ux_profile_skills_profile_lower_name", () -> rows.skill(profile, "SPRING BOOT"));
        }

        @Test
        void rechazaUnNivelDeHabilidadNoValido() {
            UUID profile = rows.profile(rows.user());
            assertViolates("ck_profile_skills_level", () -> jdbc.update(
                    "INSERT INTO profile_skills (id, profile_id, name, level) VALUES (?, ?, 'Java', 'EXPERTO')",
                    UUID.randomUUID(), profile));
        }

        @Test
        void rechazaUnaExperienciaQueTerminaAntesDeEmpezar() {
            UUID profile = rows.profile(rows.user());
            assertViolates("ck_experiences_dates", () -> jdbc.update("""
                    INSERT INTO experiences (id, profile_id, company, role, start_date, end_date, sort_order)
                    VALUES (?, ?, 'Empresa', 'Dev', DATE '2024-06-01', DATE '2024-01-01', 0)""",
                    UUID.randomUUID(), profile));
        }
    }

    @Nested
    class JobOffers {

        private void insertOffer(String extraColumns, String extraValues) {
            jdbc.update("INSERT INTO job_offers (id, user_id, raw_text, extraction_status" + extraColumns
                            + ") VALUES (?, ?, ?, 'COMPLETADA'" + extraValues + ")",
                    UUID.randomUUID(), rows.user(), TestRows.VALID_RAW_TEXT);
        }

        @Test
        void rechazaUnSalarioSinMoneda() {
            assertViolates("ck_job_offers_currency_required", () -> insertOffer(", salary_min", ", 30000"));
        }

        @Test
        void aceptaUnSalarioConMoneda() {
            assertThatCode(() -> insertOffer(", salary_min, salary_max, currency", ", 30000, 36000, 'EUR'"))
                    .doesNotThrowAnyException();
        }

        @Test
        void rechazaUnaMonedaQueNoEsIso4217() {
            assertViolates("ck_job_offers_currency_format",
                    () -> insertOffer(", salary_min, currency", ", 30000, 'eur'"));
        }

        @Test
        void rechazaUnSalarioMaximoMenorQueElMinimo() {
            assertViolates("ck_job_offers_salary_range",
                    () -> insertOffer(", salary_min, salary_max, currency", ", 40000, 30000, 'EUR'"));
        }

        @Test
        void rechazaUnTextoDeOfertaDemasiadoCorto() {
            assertViolates("ck_job_offers_raw_text_length", () -> jdbc.update(
                    "INSERT INTO job_offers (id, user_id, raw_text, extraction_status) VALUES (?, ?, ?, 'PENDIENTE')",
                    UUID.randomUUID(), rows.user(), "x".repeat(199)));
        }

        @Test
        void rechazaUnEstadoDeExtraccionNoValido() {
            assertViolates("ck_job_offers_extraction_status", () -> jdbc.update(
                    "INSERT INTO job_offers (id, user_id, raw_text, extraction_status) VALUES (?, ?, ?, 'ERROR')",
                    UUID.randomUUID(), rows.user(), TestRows.VALID_RAW_TEXT));
        }

        @Test
        void laModalidadEsObligatoriaYPorDefectoEsDesconocido() {
            // V2 (JP-BE-05): la API siempre devuelve workMode.
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO job_offers (id, user_id, raw_text, extraction_status) VALUES (?, ?, ?, 'PENDIENTE')",
                    id, rows.user(), TestRows.VALID_RAW_TEXT);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                    "SELECT work_mode FROM job_offers WHERE id = ?", String.class, id)).isEqualTo("DESCONOCIDO");

            assertViolates("work_mode", () -> insertOffer(", work_mode", ", NULL"));
        }
    }

    @Nested
    class Analyses {

        @Test
        void rechazaUnaPuntuacionDe101() {
            UUID user = rows.user();
            UUID offer = rows.offer(user);
            UUID profile = rows.profile(user);
            assertViolates("ck_match_analyses_score", () -> rows.analysis(offer, profile, 101));
        }

        @Test
        void aceptaLasPuntuacionesFrontera0y100() {
            UUID user = rows.user();
            UUID offer = rows.offer(user);
            UUID profile = rows.profile(user);
            assertThatCode(() -> {
                rows.analysis(offer, profile, 0);
                rows.analysis(offer, profile, 100);
            }).doesNotThrowAnyException();
        }

        @Test
        void rechazaUnResultadoDeRequisitoQueApuntaAUnRequisitoDeOtraOferta() {
            UUID user = rows.user();
            UUID profile = rows.profile(user);
            UUID offerA = rows.offer(user);
            UUID offerB = rows.offer(user);
            UUID analysisOfA = rows.analysis(offerA, profile);
            UUID requirementOfB = rows.requirement(offerB);

            assertViolates("fk_requirement_matches_requirement",
                    () -> rows.requirementMatch(analysisOfA, requirementOfB, offerA));
        }

        @Test
        void rechazaUnResultadoDeRequisitoQueApuntaAUnAnalisisDeOtraOferta() {
            UUID user = rows.user();
            UUID profile = rows.profile(user);
            UUID offerA = rows.offer(user);
            UUID offerB = rows.offer(user);
            UUID analysisOfA = rows.analysis(offerA, profile);
            UUID requirementOfB = rows.requirement(offerB);

            assertViolates("fk_requirement_matches_analysis",
                    () -> rows.requirementMatch(analysisOfA, requirementOfB, offerB));
        }

        @Test
        void rechazaUnDocumentoConVersionCero() {
            UUID offer = rows.offer(rows.user());
            assertViolates("ck_generated_documents_version", () -> jdbc.update("""
                    INSERT INTO generated_documents (id, offer_id, type, content_markdown, version)
                    VALUES (?, ?, 'CARTA', '# Carta', 0)""", UUID.randomUUID(), offer));
        }
    }

    @Nested
    class Applications {

        private void insertApplication(UUID user, UUID offer, String status, String appliedAt, int position) {
            jdbc.update("""
                    INSERT INTO applications (id, user_id, offer_id, status, applied_at, board_position)
                    VALUES (?, ?, ?, ?, ?::date, ?)""", UUID.randomUUID(), user, offer, status, appliedAt, position);
        }

        @Test
        void rechazaUnEstadoNoValido() {
            UUID user = rows.user();
            UUID offer = rows.offer(user);
            assertViolates("ck_applications_status", () -> insertApplication(user, offer, "PENDIENTE", null, 0));
        }

        @Test
        void rechazaUnaCandidaturaEnviadaSinFechaDeEnvio() {
            UUID user = rows.user();
            UUID offer = rows.offer(user);
            assertViolates("ck_applications_applied_at", () -> insertApplication(user, offer, "ENVIADA", null, 0));
        }

        @Test
        void aceptaUnaCandidaturaGuardadaSinFechaDeEnvio() {
            UUID user = rows.user();
            UUID offer = rows.offer(user);
            assertThatCode(() -> insertApplication(user, offer, "GUARDADA", null, 0)).doesNotThrowAnyException();
        }

        @Test
        void rechazaUnaPosicionNegativa() {
            UUID user = rows.user();
            UUID offer = rows.offer(user);
            assertViolates("ck_applications_board_position", () -> insertApplication(user, offer, "GUARDADA", null, -1));
        }

        @Test
        void rechazaUnaSegundaCandidaturaParaLaMismaOferta() {
            UUID user = rows.user();
            UUID offer = rows.offer(user);
            rows.application(user, offer);
            assertViolates("uq_applications_offer", () -> rows.application(user, offer));
        }

        @Test
        void rechazaUnaCandidaturaDeUnUsuarioQueNoEsElPropietarioDeLaOferta() {
            UUID owner = rows.user();
            UUID other = rows.user();
            UUID offerOfOwner = rows.offer(owner);
            assertViolates("fk_applications_offer_owner", () -> rows.application(other, offerOfOwner));
        }

        @Test
        void rechazaUnHistorialConElMismoEstadoDeOrigenYDestino() {
            UUID user = rows.user();
            UUID application = rows.application(user, rows.offer(user));
            assertViolates("ck_status_history_distinct", () -> rows.history(application, "ENVIADA", "ENVIADA"));
        }

        @Test
        void aceptaElHistorialDeCreacionSinEstadoDeOrigen() {
            UUID user = rows.user();
            UUID application = rows.application(user, rows.offer(user));
            assertThatCode(() -> rows.history(application, null, "GUARDADA")).doesNotThrowAnyException();
        }

        @Test
        void rechazaUnEstadoDeDestinoNoValidoEnElHistorial() {
            UUID user = rows.user();
            UUID application = rows.application(user, rows.offer(user));
            assertViolates("ck_status_history_to_status", () -> rows.history(application, "GUARDADA", "ARCHIVADA"));
        }
    }
}
