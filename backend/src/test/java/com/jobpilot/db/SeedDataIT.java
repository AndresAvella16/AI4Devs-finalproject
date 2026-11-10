package com.jobpilot.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.jobpilot.support.PostgresTestcontainer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

/**
 * CA 2: con el perfil dev se cargan los datos de prueba descritos en JP-DB-01, coherentes con la fórmula de la
 * sección 1.2 y con un seed que se puede ejecutar varias veces. pgcrypto se activa solo en esta BD de test, para
 * verificar el hash BCrypt sin depender de Spring Security.
 */
@SpringBootTest(properties = "llm.mode=fake")   // el perfil dev importa ../.env: que su LLM_MODE no cuente
@ActiveProfiles("dev")
@Import(PostgresTestcontainer.class)
class SeedDataIT {

    private static final String DEMO_EMAIL = "demo@jobpilot.dev";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private UUID demoUserId;

    @BeforeEach
    void setUp() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        demoUserId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, DEMO_EMAIL);
    }

    @Test
    void laContrasenaDelUsuarioDemoEsDemo1234ConBcryptCoste12() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, demoUserId);

        assertThat(hash).startsWith("$2a$12$");
        assertThat(jdbc.queryForObject("SELECT crypt('Demo1234!', ?) = ?", Boolean.class, hash, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT crypt('otra', ?) = ?", Boolean.class, hash, hash)).isFalse();
    }

    @Test
    void elPerfilTieneOchoHabilidadesYDosExperiencias() {
        UUID profileId = jdbc.queryForObject("SELECT id FROM profiles WHERE user_id = ?", UUID.class, demoUserId);

        assertThat(count("SELECT count(*) FROM profile_skills WHERE profile_id = ?", profileId)).isEqualTo(8);
        assertThat(count("SELECT count(*) FROM experiences WHERE profile_id = ?", profileId)).isEqualTo(2);
        assertThat(count("""
                SELECT count(*) FROM profiles
                WHERE id = ? AND headline IS NOT NULL AND summary IS NOT NULL
                  AND jsonb_array_length(languages) > 0 AND jsonb_array_length(education) > 0""", profileId))
                .isEqualTo(1);
    }

    @Test
    void hayTresOfertasAnalizadasConPuntuaciones42_68Y85() {
        List<Integer> scores = jdbc.queryForList("""
                SELECT a.score FROM match_analyses a JOIN job_offers o ON o.id = a.offer_id
                WHERE o.user_id = ? AND o.extraction_status = 'COMPLETADA'
                ORDER BY a.score""", Integer.class, demoUserId);

        assertThat(scores).containsExactly(42, 68, 85);
        assertThat(count("SELECT count(*) FROM job_offers WHERE user_id = ?", demoUserId)).isEqualTo(3);
    }

    @Test
    void cadaPuntuacionCoincideConLaFormulaAplicadaASusResultados() {
        List<Map<String, Object>> analyses = jdbc.queryForList("""
                SELECT a.id, a.offer_id, a.score, a.has_blocking_gap, jsonb_array_length(a.suggestions) AS suggestions
                FROM match_analyses a JOIN job_offers o ON o.id = a.offer_id WHERE o.user_id = ?""", demoUserId);

        assertThat(analyses).hasSize(3);
        for (Map<String, Object> analysis : analyses) {
            UUID analysisId = (UUID) analysis.get("id");
            UUID offerId = (UUID) analysis.get("offer_id");
            List<Map<String, Object>> matches = jdbc.queryForList("""
                    SELECT r.type, m.result FROM requirement_matches m
                    JOIN offer_requirements r ON r.id = m.requirement_id
                    WHERE m.analysis_id = ?""", analysisId);

            assertThat(matches).as("todos los requisitos de la oferta están evaluados")
                    .hasSize(count("SELECT count(*) FROM offer_requirements WHERE offer_id = ?", offerId));
            assertThat(((Number) analysis.get("score")).intValue()).isEqualTo(expectedScore(matches));
            assertThat(analysis.get("has_blocking_gap")).isEqualTo(matches.stream()
                    .anyMatch(m -> "OBLIGATORIO".equals(m.get("type")) && "NO_CUMPLE".equals(m.get("result"))));
            assertThat(((Number) analysis.get("suggestions")).intValue()).isBetween(3, 5);
        }
    }

    @Test
    void hayTresCandidaturasEnGuardadaEnviadaVencidaYEntrevista() {
        List<Map<String, Object>> applications = jdbc.queryForList("""
                SELECT status, applied_at, follow_up_date < CURRENT_DATE AS overdue
                FROM applications WHERE user_id = ? ORDER BY status""", demoUserId);

        assertThat(applications).extracting(a -> a.get("status"))
                .containsExactly("ENTREVISTA", "ENVIADA", "GUARDADA");
        assertThat(applications.get(0).get("applied_at")).isNotNull();
        assertThat(applications.get(1).get("applied_at")).isNotNull();
        assertThat(applications.get(1).get("overdue")).isEqualTo(true);
    }

    @Test
    void elHistorialDeCadaCandidaturaEmpiezaEnLaCreacionYTerminaEnSuEstadoActual() {
        List<Map<String, Object>> applications = jdbc.queryForList(
                "SELECT id, status FROM applications WHERE user_id = ?", demoUserId);

        for (Map<String, Object> application : applications) {
            List<Map<String, Object>> history = jdbc.queryForList("""
                    SELECT from_status, to_status FROM application_status_history
                    WHERE application_id = ? ORDER BY changed_at""", application.get("id"));

            assertThat(history).isNotEmpty();
            assertThat(history.getFirst().get("from_status")).isNull();
            assertThat(history.getFirst().get("to_status")).isEqualTo("GUARDADA");
            assertThat(history.getLast().get("to_status")).isEqualTo(application.get("status"));
            for (int i = 1; i < history.size(); i++) {
                assertThat(history.get(i).get("from_status")).isEqualTo(history.get(i - 1).get("to_status"));
            }
        }
    }

    @Test
    void elSeedSePuedeEjecutarOtraVezSinErroresNiDuplicados() {
        Map<String, Long> before = tableCounts();

        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("db/seed/R__demo_data.sql"));
        assertThatCode(() -> populator.execute(dataSource)).doesNotThrowAnyException();

        assertThat(tableCounts()).isEqualTo(before);
    }

    /** Fórmula de la sección 1.2, reescrita aquí a propósito: el seed no puede depender del código que valida. */
    private static int expectedScore(List<Map<String, Object>> matches) {
        double mandatory = average(matches, "OBLIGATORIO");
        boolean hasDesirable = matches.stream().anyMatch(m -> "DESEABLE".equals(m.get("type")));
        double raw = hasDesirable ? 0.7 * mandatory + 0.3 * average(matches, "DESEABLE") : mandatory;
        return (int) Math.round(100 * raw);
    }

    private static double average(List<Map<String, Object>> matches, String type) {
        return matches.stream()
                .filter(m -> type.equals(m.get("type")))
                .mapToDouble(m -> switch ((String) m.get("result")) {
                    case "CUMPLE" -> 1.0;
                    case "PARCIAL" -> 0.5;
                    default -> 0.0;
                })
                .average()
                .orElseThrow();
    }

    /** JP-QA-01: el seed de E2E (db/e2e-seed) no se carga en dev. */
    @Test
    void noCargaElSeedDeE2e() {
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", "e2e@jobpilot.test")).isZero();
        assertThat(count("SELECT count(*) FROM flyway_schema_history WHERE script LIKE ?", "%e2e_data%")).isZero();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private Map<String, Long> tableCounts() {
        return jdbc.queryForList("""
                        SELECT 'users' AS t, count(*) AS n FROM users
                        UNION ALL SELECT 'profiles', count(*) FROM profiles
                        UNION ALL SELECT 'profile_skills', count(*) FROM profile_skills
                        UNION ALL SELECT 'experiences', count(*) FROM experiences
                        UNION ALL SELECT 'job_offers', count(*) FROM job_offers
                        UNION ALL SELECT 'offer_requirements', count(*) FROM offer_requirements
                        UNION ALL SELECT 'match_analyses', count(*) FROM match_analyses
                        UNION ALL SELECT 'requirement_matches', count(*) FROM requirement_matches
                        UNION ALL SELECT 'applications', count(*) FROM applications
                        UNION ALL SELECT 'application_status_history', count(*) FROM application_status_history""")
                .stream()
                .collect(java.util.stream.Collectors.toMap(r -> (String) r.get("t"), r -> ((Number) r.get("n")).longValue()));
    }
}
