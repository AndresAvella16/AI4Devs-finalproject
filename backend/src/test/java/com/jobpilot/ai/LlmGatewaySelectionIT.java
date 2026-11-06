package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.JobPilotApplication;
import com.jobpilot.support.EmptyDatabases;
import com.jobpilot.support.PostgresTestcontainer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * CA 1 y 2: {@code LLM_MODE} elige la implementación de {@link LlmGateway} al arrancar la aplicación completa
 * (incluido el {@code EnvironmentPostProcessor}). Ningún caso llama a la API de Anthropic.
 */
@Testcontainers
class LlmGatewaySelectionIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestcontainer.IMAGE);

    private static ConfigurableApplicationContext start(String... extraArgs) throws Exception {
        List<String> args = new ArrayList<>(List.of(
                "--server.port=0",
                "--DB_URL=" + EmptyDatabases.create(POSTGRES, "llm"),
                "--POSTGRES_USER=" + POSTGRES.getUsername(),
                "--POSTGRES_PASSWORD=" + POSTGRES.getPassword(),
                "--JWT_SECRET=test-only-secreto-jwt-ficticio-de-al-menos-32-bytes"));
        args.addAll(List.of(extraArgs));
        return new SpringApplicationBuilder(JobPilotApplication.class).run(args.toArray(String[]::new));
    }

    @Test
    void sinLlmModeNiApiKeySeUsaElFakeYNoHayClienteDeAnthropic() throws Exception {
        try (ConfigurableApplicationContext context = start("--ANTHROPIC_API_KEY=")) {
            LlmGateway gateway = context.getBean(LlmGateway.class);

            assertThat(gateway).isInstanceOf(FakeLlmGateway.class);
            assertThat(gateway.modelName()).isEqualTo("fake");
            assertThat(context.getBeanProvider(ChatModel.class).getIfAvailable()).isNull();
            assertThat(context.getBeanProvider(StructuredLlmCall.class).getIfAvailable()).isNull();
        }
    }

    @Test
    void enModoRealConApiKeySeUsaElAdaptadorDeSpringAiConElModeloPorDefecto() throws Exception {
        try (ConfigurableApplicationContext context = start(
                "--LLM_MODE=real", "--ANTHROPIC_API_KEY=sk-ant-test-only-ficticia", "--ANTHROPIC_MODEL=")) {
            LlmGateway gateway = context.getBean(LlmGateway.class);

            assertThat(gateway).isInstanceOf(SpringAiLlmGateway.class);
            assertThat(gateway.modelName()).isEqualTo("claude-sonnet-5-5");
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(AnthropicChatModel.class);
            assertThat(context.getBean(StructuredLlmCall.class)).isNotNull();
        }
    }

    @Test
    void enModoRealSeUsaElModeloDeAnthropicModel() throws Exception {
        try (ConfigurableApplicationContext context = start(
                "--LLM_MODE=real", "--ANTHROPIC_API_KEY=sk-ant-test-only-ficticia",
                "--ANTHROPIC_MODEL=claude-haiku-5-5")) {
            assertThat(context.getBean(LlmGateway.class).modelName()).isEqualTo("claude-haiku-5-5");
        }
    }

    /** Falla en el EnvironmentPostProcessor, antes de crear ningún bean ni conectar con la BD. */
    @Test
    void enModoRealSinApiKeyLaAplicacionNoArranca() {
        assertThatThrownBy(() -> start("--LLM_MODE=real", "--ANTHROPIC_API_KEY="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_MODE=real")
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }
}
