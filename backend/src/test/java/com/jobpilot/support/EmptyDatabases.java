package com.jobpilot.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Crea bases de datos nuevas y vacías dentro de un contenedor compartido, para los tests que arrancan la aplicación
 * varias veces. No se puede reutilizar una: después de dev (que aplica el seed repetible), Flyway se niega, con
 * razón, a arrancar otro perfil sobre esa BD.
 */
public final class EmptyDatabases {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private EmptyDatabases() {
    }

    /** Crea la BD {@code <prefix>_<n>} y devuelve su URL JDBC. */
    public static String create(PostgreSQLContainer postgres, String prefix) throws SQLException {
        String name = prefix + "_" + COUNTER.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + name;
    }
}
