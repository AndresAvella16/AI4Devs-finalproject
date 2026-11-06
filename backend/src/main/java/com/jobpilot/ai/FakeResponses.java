package com.jobpilot.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Lee las respuestas fijas del modo fake de {@code classpath:fake-llm/<nombre>.json}, con el mismo rigor que las reales. */
class FakeResponses {

    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    <T> T load(String name, Class<T> type) {
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Nombre de respuesta fake no válido: " + name);
        }
        String path = "fake-llm/" + name + ".json";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("No existe la respuesta fake classpath:" + path);
        }
        try (InputStream in = resource.getInputStream()) {
            return mapper.readValue(in, type);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("No se puede leer la respuesta fake classpath:" + path, e);
        }
    }
}
