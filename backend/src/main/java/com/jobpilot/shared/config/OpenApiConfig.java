package com.jobpilot.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos del contrato OpenAPI que publica springdoc en {@code /v3/api-docs} y Swagger UI. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI jobPilotOpenApi() {
        return new OpenAPI().info(new Info()
                .title("JobPilot API")
                .version("1.0.0")
                .description("API del copiloto de búsqueda de empleo JobPilot."));
    }
}
