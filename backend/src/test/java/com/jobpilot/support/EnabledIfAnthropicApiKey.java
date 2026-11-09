package com.jobpilot.support;

import java.io.IOException;
import java.io.Reader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Salta el test (sin fallar) si no hay {@code ANTHROPIC_API_KEY} con valor. La busca donde la resolvería Spring con
 * el perfil {@code llm-live}, por orden de prioridad: propiedad del sistema, variable de entorno y el {@code .env}
 * de la raíz del repo ({@code spring.config.import} de {@code application-llm-live.yml}). Se evalúa antes de crear
 * el contexto: sin clave, {@code LLM_MODE=real} no arrancaría.
 *
 * <p>Nunca incluye el valor de la clave en el motivo ni en ninguna otra salida.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledIfAnthropicApiKey.Condition.class)
public @interface EnabledIfAnthropicApiKey {

    String NAME = "ANTHROPIC_API_KEY";

    /** El mismo fichero que importa application-llm-live.yml; la ruta es relativa a backend/. */
    String DOT_ENV = "../.env";

    final class Condition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (hasText(System.getProperty(NAME)) || hasText(System.getenv(NAME)) || hasText(fromDotEnv())) {
                return ConditionEvaluationResult.enabled(NAME + " definida");
            }
            return ConditionEvaluationResult.disabled(
                    NAME + " no está definida en el entorno ni en " + DOT_ENV + ": se saltan los tests llm-live");
        }

        private static String fromDotEnv() {
            Path file = Path.of(DOT_ENV);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                return null;
            }
            return properties.getProperty(NAME);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
