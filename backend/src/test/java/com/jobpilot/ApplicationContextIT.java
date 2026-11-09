package com.jobpilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.ai.FakeLlmGateway;
import com.jobpilot.ai.LlmGateway;
import com.jobpilot.support.EmptyDatabases;
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
import org.springframework.ai.chat.model.ChatModel;
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

    /** JP-BE-03: sin LLM_MODE ni ANTHROPIC_API_KEY, todos los perfiles arrancan con el LLM simulado. */
    @ParameterizedTest
    @ValueSource(strings = {"dev", "e2e", "prod"})
    void arrancaConCadaPerfilEnModoFakeSinApiKey(String profile) throws Exception {
        try (ConfigurableApplicationContext context = start(profile)) {
            assertThat(context.getBean(LlmGateway.class)).isInstanceOf(FakeLlmGateway.class);
            assertThat(context.getBeanProvider(ChatModel.class).getIfAvailable()).isNull();
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

    /** Cada arranque usa una base de datos nueva y vacía (ver {@link EmptyDatabases}). */
    private static ConfigurableApplicationContext start(String profile) throws Exception {
        return new SpringApplicationBuilder(JobPilotApplication.class)
                .profiles(profile)
                .run("--server.port=0",
                        "--DB_URL=" + EmptyDatabases.create(POSTGRES, "ctx_" + profile),
                        "--POSTGRES_USER=" + POSTGRES.getUsername(),
                        "--POSTGRES_PASSWORD=" + POSTGRES.getPassword(),
                        "--JWT_SECRET=test-only-secreto-jwt-ficticio-de-al-menos-32-bytes",
                        // Modo fake y sin key aunque la máquina (o el .env que importa dev) digan otra cosa. Se usa
                        // --llm.mode: un argumento --LLM_MODE no se traduce a llm.mode (ver LlmGatewaySelectionIT).
                        "--llm.mode=fake",
                        "--ANTHROPIC_API_KEY=");
    }

    /** Código de estado sin seguir redirecciones (el HttpClient por defecto no las sigue). */
    private static int statusOf(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
