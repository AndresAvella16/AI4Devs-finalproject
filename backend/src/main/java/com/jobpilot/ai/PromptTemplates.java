package com.jobpilot.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

/**
 * Plantillas versionadas en {@code classpath:prompts/<nombre>.st} (sintaxis StringTemplate, variables entre
 * llaves). {@code _system-base.st} contiene las reglas comunes a todos los prompts de sistema y se usa tal cual,
 * sin renderizar. Solo se renderizan variables puestas por el código: los datos del usuario nunca pasan por aquí.
 */
class PromptTemplates {

    static final String SYSTEM_BASE = "_system-base";

    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_][a-z0-9-]*");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /** Reglas comunes (rol, datos no confiables entre delimitadores, solo JSON). */
    String systemBase() {
        return load(SYSTEM_BASE);
    }

    /** Renderiza la plantilla con las variables dadas; falla si falta alguna. */
    String render(String name, Map<String, Object> variables) {
        String template = load(name);
        return variables.isEmpty() ? template : new PromptTemplate(template).render(variables);
    }

    private String load(String name) {
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Nombre de plantilla no válido: " + name);
        }
        return cache.computeIfAbsent(name, PromptTemplates::read);
    }

    private static String read(String name) {
        String path = "prompts/" + name + ".st";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("No existe la plantilla classpath:" + path);
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("No se puede leer classpath:" + path, e);
        }
    }
}
