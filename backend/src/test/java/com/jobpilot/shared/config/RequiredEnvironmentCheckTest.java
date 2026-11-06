package com.jobpilot.shared.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RequiredEnvironmentCheckTest {

    private static final List<String> REQUIRED = List.of("DB_URL", "POSTGRES_USER", "POSTGRES_PASSWORD");

    @Test
    void noFallaSiEstanTodasLasVariables() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db:5432/jobpilot")
                .withProperty("POSTGRES_USER", "jobpilot")
                .withProperty("POSTGRES_PASSWORD", "x");

        assertThatCode(() -> new RequiredEnvironmentCheck(env, REQUIRED)).doesNotThrowAnyException();
    }

    @Test
    void fallaYNombraTodasLasVariablesQueFaltanOEstanVacias() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db:5432/jobpilot")
                .withProperty("POSTGRES_PASSWORD", "   ");

        assertThatThrownBy(() -> new RequiredEnvironmentCheck(env, REQUIRED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSTGRES_USER")
                .hasMessageContaining("POSTGRES_PASSWORD")
                .hasMessageNotContaining("DB_URL");
    }

    @Test
    void elPerfilProdExigeLasVariablesDeLaBaseDeDatosYElSecretoJwt() {
        assertThatThrownBy(() -> new RequiredEnvironmentCheck(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageContaining("POSTGRES_USER")
                .hasMessageContaining("POSTGRES_PASSWORD")
                .hasMessageContaining("JWT_SECRET");
    }
}
