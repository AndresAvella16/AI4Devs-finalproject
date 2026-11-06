package com.jobpilot.shared.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * En el perfil {@code prod}, impide que la aplicación arranque si falta alguna variable de entorno obligatoria
 * o está vacía. Se comprueba al crear el bean, antes que la conexión a la base de datos, para dar un mensaje claro.
 * Los tickets que añadan secretos nuevos (JWT, Anthropic…) deben añadirlos a {@link #PROD_REQUIRED}.
 */
@Component
@Profile("prod")
public class RequiredEnvironmentCheck {

    static final List<String> PROD_REQUIRED = List.of("DB_URL", "POSTGRES_USER", "POSTGRES_PASSWORD");

    @Autowired
    public RequiredEnvironmentCheck(Environment environment) {
        this(environment, PROD_REQUIRED);
    }

    RequiredEnvironmentCheck(Environment environment, List<String> required) {
        List<String> missing = required.stream()
                .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Faltan variables de entorno obligatorias (o están vacías): " + String.join(", ", missing));
        }
    }
}
