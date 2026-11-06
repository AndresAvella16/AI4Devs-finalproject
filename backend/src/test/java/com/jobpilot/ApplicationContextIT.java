package com.jobpilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.support.PostgresTestcontainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Arranca la aplicación completa con cada perfil contra un PostgreSQL 16 vacío.
 * Las variables de entorno se pasan como argumentos para no depender de la máquina.
 */
@Testcontainers
class ApplicationContextIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestcontainer.IMAGE);

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final AtomicInteger DATABASES = new AtomicInteger();

    @ParameterizedTest
    @ValueSource(strings = {"dev", "e2e", "prod"})
    void arrancaConCadaPerfilContraPostgres16(String profile) throws Exception {
        try (ConfigurableApplicationContext context = start(profile)) {
            assertThat(context.isActive()).isTrue();
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");

            try (Connection connection = context.getBean(DataSource.class).getConnection()) {
                assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
            }
        }
    }

    @ParameterizedTest(name = "{0}: api-docs {1}, swagger-ui {2}")
    @CsvSource({
            "dev,  200, 302",
            "e2e,  200, 302",
            "prod, 404, 404"
    })
    void swaggerSoloEstaActivoFueraDeProd(String profile, int apiDocsStatus, int swaggerUiStatus) throws Exception {
        try (ConfigurableApplicationContext context = start(profile)) {
            String baseUrl = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");

            assertThat(statusOf(baseUrl + "/v3/api-docs")).isEqualTo(apiDocsStatus);
            assertThat(statusOf(baseUrl + "/swagger-ui.html")).isEqualTo(swaggerUiStatus);
            assertThat(statusOf(baseUrl + "/actuator/health")).isEqualTo(200);
        }
    }

    /**
     * Cada arranque usa una base de datos nueva y vacía. No se puede compartir una entre perfiles: después de dev
     * (que aplica el seed repetible), Flyway se niega, con razón, a arrancar e2e o prod sobre esa BD.
     */
    private static ConfigurableApplicationContext start(String profile) throws Exception {
        return new SpringApplicationBuilder(JobPilotApplication.class)
                .profiles(profile)
                .run("--server.port=0",
                        "--DB_URL=" + emptyDatabaseUrl(profile),
                        "--POSTGRES_USER=" + POSTGRES.getUsername(),
                        "--POSTGRES_PASSWORD=" + POSTGRES.getPassword(),
                        "--JWT_SECRET=test-only-secreto-jwt-ficticio-de-al-menos-32-bytes");
    }

    private static String emptyDatabaseUrl(String profile) throws Exception {
        String name = "ctx_" + profile + "_" + DATABASES.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + name;
    }

    /** Código de estado sin seguir redirecciones (el HttpClient por defecto no las sigue). */
    private static int statusOf(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
