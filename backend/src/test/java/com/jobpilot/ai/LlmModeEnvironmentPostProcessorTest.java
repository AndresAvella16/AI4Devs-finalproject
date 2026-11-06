package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * CA 1 y 2: {@code llm.mode} decide si se activa la autoconfiguración de Anthropic, exige la API key en modo real
 * y rellena el modelo por defecto si {@code ANTHROPIC_MODEL} está vacío.
 */
class LlmModeEnvironmentPostProcessorTest {

    private static final String CHAT_PROVIDER = "spring.ai.model.chat";
    private static final String MODEL = "spring.ai.anthropic.chat.model";

    private final LlmModeEnvironmentPostProcessor processor = new LlmModeEnvironmentPostProcessor();

    private static StandardEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", new HashMap<>(properties)));
        return environment;
    }

    private StandardEnvironment process(Map<String, Object> properties) {
        StandardEnvironment environment = environment(properties);
        processor.postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    // ---- Modo fake ----

    @Test
    void sinLlmModeSeUsaFakeYSeDesactivaLaAutoconfiguracionDeAnthropic() {
        StandardEnvironment environment = process(Map.of());

        assertThat(environment.getProperty(CHAT_PROVIDER)).isEqualTo("none");
    }

    @Test
    void enModoFakeNoHaceFaltaApiKey() {
        StandardEnvironment environment = process(Map.of("llm.mode", "fake", "spring.ai.anthropic.api-key", ""));

        assertThat(environment.getProperty(CHAT_PROVIDER)).isEqualTo("none");
    }

    @Test
    void elModoFakeTambienDesactivaAnthropicAunqueAlguienLoHayaActivadoAMano() {
        StandardEnvironment environment = process(Map.of("llm.mode", "fake", CHAT_PROVIDER, "anthropic"));

        assertThat(environment.getProperty(CHAT_PROVIDER)).isEqualTo("none");
    }

    // ---- Modo real ----

    @Test
    void enModoRealConApiKeySeActivaAnthropicConElModeloPorDefectoSiNoSeIndica() {
        StandardEnvironment environment = process(Map.of(
                "llm.mode", "real", "spring.ai.anthropic.api-key", "sk-ant-ficticia", MODEL, ""));

        assertThat(environment.getProperty(CHAT_PROVIDER)).isEqualTo("anthropic");
        assertThat(environment.getProperty(MODEL)).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void enModoRealSeRespetaElModeloIndicado() {
        StandardEnvironment environment = process(Map.of(
                "llm.mode", "real", "spring.ai.anthropic.api-key", "sk-ant-ficticia", MODEL, "claude-opus-5-5"));

        assertThat(environment.getProperty(MODEL)).isEqualTo("claude-opus-5-5");
    }

    @ParameterizedTest(name = "api-key \"{0}\"")
    @ValueSource(strings = {"", "   "})
    void enModoRealSinApiKeyLaAppNoArranca(String apiKey) {
        assertThatThrownBy(() -> process(Map.of("llm.mode", "real", "spring.ai.anthropic.api-key", apiKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY")
                .hasMessageContaining("LLM_MODE=real");
    }

    @Test
    void enModoRealSinLaPropiedadDeApiKeyLaAppNoArranca() {
        assertThatThrownBy(() -> process(Map.of("llm.mode", "real")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    // ---- Valores no válidos ----

    @ParameterizedTest(name = "llm.mode \"{0}\"")
    @ValueSource(strings = {"reall", "REAL", "real ", " fake", ""})
    void unModoQueNoEsExactamenteFakeORealImpideArrancar(String mode) {
        assertThatThrownBy(() -> process(Map.of("llm.mode", mode)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_MODE")
                .hasMessageContaining("fake")
                .hasMessageContaining("real");
    }
}
