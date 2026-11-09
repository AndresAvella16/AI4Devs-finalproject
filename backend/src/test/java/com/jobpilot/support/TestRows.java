package com.jobpilot.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inserta filas mínimas y válidas con SQL directo, para probar el esquema sin pasar por las entidades JPA.
 * Cada método devuelve el id generado.
 */
public final class TestRows {

    /** Texto de oferta con la longitud mínima que exige {@code ck_job_offers_raw_text_length} (200). */
    public static final String VALID_RAW_TEXT = "Oferta de prueba. ".repeat(12);

    /** Sugerencias de un análisis: entre 3 y 5, como exige {@code ck_match_analyses_suggestions_count}. */
    public static final String VALID_SUGGESTIONS = "[\"Sugerencia 1\", \"Sugerencia 2\", \"Sugerencia 3\"]";

    private final JdbcTemplate jdbc;

    public TestRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID user(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name) VALUES (?, ?, 'hash', 'Usuario Test')",
                id, email);
        return id;
    }

    public UUID user() {
        return user("user-" + UUID.randomUUID() + "@test.dev");
    }

    public UUID profile(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO profiles (id, user_id) VALUES (?, ?)", id, userId);
        return id;
    }

    public UUID skill(UUID profileId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO profile_skills (id, profile_id, name, level, years) VALUES (?, ?, ?, 'AVANZADO', 2)",
                id, profileId, name);
        return id;
    }

    public UUID experience(UUID profileId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO experiences (id, profile_id, company, role, start_date, sort_order)
                VALUES (?, ?, 'Empresa', 'Desarrollador', DATE '2024-01-01', 0)""", id, profileId);
        return id;
    }

    public UUID offer(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO job_offers (id, user_id, raw_text, extraction_status) VALUES (?, ?, ?, 'COMPLETADA')",
                id, userId, VALID_RAW_TEXT);
        return id;
    }

    public UUID requirement(UUID offerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO offer_requirements (id, offer_id, description, type, category, sort_order)
                VALUES (?, ?, 'Java', 'OBLIGATORIO', 'TECNICO', 0)""", id, offerId);
        return id;
    }

    public UUID analysis(UUID offerId, UUID profileId, int score) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO match_analyses (id, offer_id, profile_id, score, has_blocking_gap, summary, suggestions,
                                            llm_model)
                VALUES (?, ?, ?, ?, false, 'Resumen', ?::jsonb, 'fake')""", id, offerId, profileId, score,
                VALID_SUGGESTIONS);
        return id;
    }

    public UUID analysis(UUID offerId, UUID profileId) {
        return analysis(offerId, profileId, 50);
    }

    public UUID requirementMatch(UUID analysisId, UUID requirementId, UUID offerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO requirement_matches (id, analysis_id, requirement_id, offer_id, result)
                VALUES (?, ?, ?, ?, 'CUMPLE')""", id, analysisId, requirementId, offerId);
        return id;
    }

    public UUID document(UUID offerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO generated_documents (id, offer_id, type, content_markdown, version)
                VALUES (?, ?, 'CARTA', '# Carta', 1)""", id, offerId);
        return id;
    }

    public UUID application(UUID userId, UUID offerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO applications (id, user_id, offer_id, board_position) VALUES (?, ?, ?, 0)",
                id, userId, offerId);
        return id;
    }

    public UUID history(UUID applicationId, String from, String to) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO application_status_history (id, application_id, from_status, to_status)
                VALUES (?, ?, ?, ?)""", id, applicationId, from, to);
        return id;
    }
}
