package com.jobpilot.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.support.PostgresTestcontainer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** CA 1: Flyway aplica V1 sobre un PostgreSQL 16 vacío y crea las 11 tablas y los índices de la sección 3.2. */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class SchemaMigrationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAplicaLaMigracionInicial() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versions).startsWith("1");
    }

    @Test
    void existenLasOnceTablasDelModelo() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'""", String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "users", "profiles", "profile_skills", "experiences", "job_offers", "offer_requirements",
                "match_analyses", "requirement_matches", "generated_documents", "applications",
                "application_status_history");
    }

    @Test
    void existenLosIndicesDeLaSeccion32() {
        Map<String, String> indexes = jdbc.queryForList(
                        "SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'public'")
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("indexname"), row -> (String) row.get("indexdef")));

        Map<String, String> expected = Map.ofEntries(
                Map.entry("uq_users_email", "UNIQUE INDEX uq_users_email ON public.users USING btree (email)"),
                Map.entry("uq_profiles_user_id", "UNIQUE INDEX uq_profiles_user_id ON public.profiles USING btree (user_id)"),
                Map.entry("ux_profile_skills_profile_lower_name",
                        "UNIQUE INDEX ux_profile_skills_profile_lower_name ON public.profile_skills USING btree (profile_id, lower((name)::text))"),
                Map.entry("ix_experiences_profile_sort",
                        "INDEX ix_experiences_profile_sort ON public.experiences USING btree (profile_id, sort_order)"),
                Map.entry("ix_job_offers_user_created",
                        "INDEX ix_job_offers_user_created ON public.job_offers USING btree (user_id, created_at DESC)"),
                Map.entry("uq_job_offers_id_user",
                        "UNIQUE INDEX uq_job_offers_id_user ON public.job_offers USING btree (id, user_id)"),
                Map.entry("uq_offer_requirements_offer_id",
                        "UNIQUE INDEX uq_offer_requirements_offer_id ON public.offer_requirements USING btree (offer_id, id)"),
                Map.entry("ix_match_analyses_offer_created",
                        "INDEX ix_match_analyses_offer_created ON public.match_analyses USING btree (offer_id, created_at DESC)"),
                Map.entry("ix_match_analyses_profile",
                        "INDEX ix_match_analyses_profile ON public.match_analyses USING btree (profile_id)"),
                Map.entry("uq_match_analyses_offer_id",
                        "UNIQUE INDEX uq_match_analyses_offer_id ON public.match_analyses USING btree (offer_id, id)"),
                Map.entry("uq_requirement_matches_analysis_requirement",
                        "UNIQUE INDEX uq_requirement_matches_analysis_requirement ON public.requirement_matches USING btree (analysis_id, requirement_id)"),
                Map.entry("ix_requirement_matches_offer_requirement",
                        "INDEX ix_requirement_matches_offer_requirement ON public.requirement_matches USING btree (offer_id, requirement_id)"),
                Map.entry("uq_generated_documents_offer_type_version",
                        "UNIQUE INDEX uq_generated_documents_offer_type_version ON public.generated_documents USING btree (offer_id, type, version)"),
                Map.entry("uq_applications_offer",
                        "UNIQUE INDEX uq_applications_offer ON public.applications USING btree (offer_id)"),
                Map.entry("ix_applications_user_status_position",
                        "INDEX ix_applications_user_status_position ON public.applications USING btree (user_id, status, board_position)"),
                Map.entry("ix_status_history_application_changed",
                        "INDEX ix_status_history_application_changed ON public.application_status_history USING btree (application_id, changed_at)"));

        assertThat(indexes).containsKeys(expected.keySet().toArray(String[]::new));
        expected.forEach((name, definition) -> assertThat(indexes.get(name)).endsWith(definition));
    }

    @Test
    void lasClavesPrimariasSonUuid() {
        List<String> nonUuidIds = jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = 'id' AND data_type <> 'uuid'
                  AND table_name <> 'flyway_schema_history'""", String.class);
        assertThat(nonUuidIds).isEmpty();
    }
}
