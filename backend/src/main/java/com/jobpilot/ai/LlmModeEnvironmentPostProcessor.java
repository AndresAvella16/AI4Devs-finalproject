package com.jobpilot.ai;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Traduce {@code llm.mode} ({@code LLM_MODE}) a la configuración de Spring AI antes de la autoconfiguración. Se
 * registra en {@code META-INF/spring.factories}.
 *
 * <ul>
 *   <li>{@code fake} (por defecto): {@code spring.ai.model.chat=none}, así que no se crea el cliente de Anthropic
 *       y no hace falta API key.</li>
 *   <li>{@code real}: {@code spring.ai.model.chat=anthropic}. Exige {@code ANTHROPIC_API_KEY} y, si
 *       {@code ANTHROPIC_MODEL} está vacío, usa {@value #DEFAULT_MODEL}.</li>
 *   <li>Cualquier otro valor (también con espacios o mayúsculas): la aplicación no arranca.</li>
 * </ul>
 */
public class LlmModeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Modelo por defecto (sección 2.2): suficiente para extracción y encaje, y más barato y rápido que Opus. */
    static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    static final String MODE = "llm.mode";
    static final String CHAT_PROVIDER = "spring.ai.model.chat";
    static final String API_KEY = "spring.ai.anthropic.api-key";
    static final String MODEL = "spring.ai.anthropic.chat.model";

    private static final String PROPERTY_SOURCE = "jobpilotLlmMode";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String mode = environment.getProperty(MODE, "fake");
        Map<String, Object> overrides = new HashMap<>();
        switch (mode) {
            case "fake" -> overrides.put(CHAT_PROVIDER, "none");
            case "real" -> {
                String apiKey = environment.getProperty(API_KEY);
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "LLM_MODE=real requiere la variable de entorno ANTHROPIC_API_KEY. "
                                    + "Defínela o usa LLM_MODE=fake para desarrollar sin API key.");
                }
                overrides.put(CHAT_PROVIDER, "anthropic");
                String model = environment.getProperty(MODEL);
                if (model == null || model.isBlank()) {
                    overrides.put(MODEL, DEFAULT_MODEL);
                }
            }
            default -> throw new IllegalStateException("LLM_MODE debe valer exactamente 'fake' o 'real' "
                    + "(en minúsculas y sin espacios); valor recibido: '" + mode + "'");
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, overrides));
    }

    /** Después de cargar application.yml y los perfiles (ConfigDataEnvironmentPostProcessor). */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
