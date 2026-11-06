package com.jobpilot.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * CA 6: el perfil prod aplica las migraciones pero no carga los datos de prueba. Las variables obligatorias se
 * rellenan solo para superar RequiredEnvironmentCheck; la conexión real la aporta Testcontainers.
 */
@SpringBootTest(properties = {"DB_URL=jdbc:postgresql://ignorado", "POSTGRES_USER=ignorado", "POSTGRES_PASSWORD=ignorado",
        "JWT_SECRET=test-only-secreto-jwt-ficticio-de-al-menos-32-bytes"})
@ActiveProfiles("prod")
@Import(PostgresTestcontainer.class)
class ProdNoSeedIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void noCargaLosDatosDePrueba() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE script LIKE '%demo_data%'", Integer.class)).isZero();
    }

    @Test
    void siAplicaLaMigracionInicial() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class)).isOne();
    }
}
