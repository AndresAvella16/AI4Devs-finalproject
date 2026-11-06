package com.jobpilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.support.PostgresTestcontainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
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

    private static ConfigurableApplicationContext start(String profile) {
        return new SpringApplicationBuilder(JobPilotApplication.class)
                .profiles(profile)
                .run("--server.port=0",
                        "--DB_URL=" + POSTGRES.getJdbcUrl(),
                        "--POSTGRES_USER=" + POSTGRES.getUsername(),
                        "--POSTGRES_PASSWORD=" + POSTGRES.getPassword());
    }

    /** Código de estado sin seguir redirecciones (el HttpClient por defecto no las sigue). */
    private static int statusOf(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
