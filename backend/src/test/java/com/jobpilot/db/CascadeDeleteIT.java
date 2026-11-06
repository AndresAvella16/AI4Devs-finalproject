package com.jobpilot.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.support.PostgresTestcontainer;
import com.jobpilot.support.TestRows;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** CA 5: borrar un usuario elimina en cascada todos sus datos y no toca los de otros usuarios. */
@SpringBootTest
@Import(PostgresTestcontainer.class)
@Transactional
class CascadeDeleteIT {

    private static final List<String> TABLES = List.of(
            "users", "profiles", "profile_skills", "experiences", "job_offers", "offer_requirements",
            "match_analyses", "requirement_matches", "generated_documents", "applications",
            "application_status_history");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void borrarUnUsuarioEliminaEnCascadaTodosSusDatos() {
        TestRows rows = new TestRows(jdbc);
        Map<String, Long> before = counts();
        UUID deleted = createUserWithAllData(rows);
        createUserWithAllData(rows);

        Map<String, Long> withBoth = counts();
        TABLES.forEach(table -> assertThat(withBoth.get(table))
                .as("filas de %s con los dos usuarios", table)
                .isEqualTo(before.get(table) + 2));

        jdbc.update("DELETE FROM users WHERE id = ?", deleted);

        Map<String, Long> after = counts();
        TABLES.forEach(table -> assertThat(after.get(table))
                .as("filas de %s tras borrar un usuario", table)
                .isEqualTo(before.get(table) + 1));
    }

    /** Crea exactamente una fila en cada una de las 11 tablas para un usuario nuevo. */
    private static UUID createUserWithAllData(TestRows rows) {
        UUID user = rows.user();
        UUID profile = rows.profile(user);
        rows.skill(profile, "Java");
        rows.experience(profile);
        UUID offer = rows.offer(user);
        UUID requirement = rows.requirement(offer);
        UUID analysis = rows.analysis(offer, profile);
        rows.requirementMatch(analysis, requirement, offer);
        rows.document(offer);
        UUID application = rows.application(user, offer);
        rows.history(application, null, "GUARDADA");
        return user;
    }

    private Map<String, Long> counts() {
        return TABLES.stream().collect(Collectors.toMap(Function.identity(),
                table -> jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)));
    }
}
