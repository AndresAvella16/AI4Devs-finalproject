package com.jobpilot.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL 16 real para los tests de integración. Se importa con {@code @Import(PostgresTestcontainer.class)}. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    public static final String IMAGE = "postgres:16";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(IMAGE);
    }
}
