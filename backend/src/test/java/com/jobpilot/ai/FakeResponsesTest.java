package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** El modo fake lee respuestas fijas de {@code classpath:fake-llm/<nombre>.json}. */
class FakeResponsesTest {

    private final FakeResponses responses = new FakeResponses();

    @Test
    void leeUnJsonDelClasspathAUnRecord() {
        assertThat(responses.load("test-sample", EchoResult.class))
                .isEqualTo(new EchoResult("respuesta fija de prueba", List.of("uno", "dos")));
    }

    @Test
    void unFicheroQueNoExisteDaUnErrorQueNombraLaRuta() {
        assertThatThrownBy(() -> responses.load("no-existe", EchoResult.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fake-llm/no-existe.json");
    }

    @Test
    void unJsonRotoDaUnErrorQueNombraLaRuta() {
        assertThatThrownBy(() -> responses.load("test-broken", EchoResult.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fake-llm/test-broken.json");
    }

    @Test
    void elNombreNoPuedeSalirDeLaCarpetaFakeLlm() {
        assertThatThrownBy(() -> responses.load("../application", EchoResult.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
