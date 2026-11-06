package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
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
    void laExtraccionFakeDeOfertaCumpleLasMismasReglasQueUnaReal() {
        OfferExtraction extraction = new FakeLlmGateway(responses).extractOffer("cualquier texto");

        assertThat(Validation.buildDefaultValidatorFactory().getValidator().validate(extraction)).isEmpty();
        assertThat(new OfferExtractionValidator().validate(extraction)).isEmpty();
        // La oferta del Ejemplo 1 del readme: 3 obligatorios y 3 deseables (la usan los IT y los E2E).
        assertThat(extraction.title()).isEqualTo("Desarrollador/a Java Junior");
        assertThat(extraction.requirements()).hasSize(6);
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
