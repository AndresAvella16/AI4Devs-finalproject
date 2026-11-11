package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.document.domain.DocumentType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Reglas de la respuesta de {@link LlmGateway#generateDocument}: el prompt pide 250–400 palabras para la carta, pero
 * solo se rechaza (→ reintento → 502) fuera de 200–500, para no pagar reintentos por una carta de 245 palabras. El CV
 * adaptado no tiene límite de palabras; los dos, como mucho 20.000 caracteres (el máximo de una edición).
 */
class GeneratedDocumentValidatorTest {

    private static final Validator BEAN_VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static String words(int count) {
        return "palabra ".repeat(count).strip();
    }

    private static GeneratedDocumentContent content(String markdown) {
        return new GeneratedDocumentContent(markdown);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 250, 400, 500})
    void unaCartaDeEntre200Y500PalabrasEsValida(int count) {
        assertThat(new GeneratedDocumentValidator(DocumentType.CARTA).validate(content(words(count)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 199, 501, 900})
    void unaCartaFueraDe200a500PalabrasNoEsValidaYElProblemaSoloDiceElRecuento(int count) {
        var problems = new GeneratedDocumentValidator(DocumentType.CARTA).validate(content(words(count)));

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst()).contains(String.valueOf(count)).doesNotContain("palabra ");
    }

    @Test
    void losSimbolosDeMarkdownNoCuentanComoPalabras() {
        String markdown = "# Carta\n\n- " + words(150) + "\n\n> " + words(48) + "\n\n---\n\n**Atentamente,**";

        assertThat(GeneratedDocumentValidator.countWords(markdown)).isEqualTo(200);
    }

    @Test
    void lasPalabrasConAcentosYSignosCuentanUnaVez() {
        assertThat(GeneratedDocumentValidator.countWords("Estimado/a equipo: ¡me encantaría unirme! — Atentamente,"))
                .isEqualTo(6);
    }

    @Test
    void elCvAdaptadoNoTieneLimiteDePalabras() {
        GeneratedDocumentValidator validator = new GeneratedDocumentValidator(DocumentType.CV_ADAPTADO);

        assertThat(validator.validate(content(words(50)))).isEmpty();
        assertThat(validator.validate(content(words(1500)))).isEmpty();
    }

    @Test
    void beanValidationRechazaElContenidoVacioOMayorDe20000Caracteres() {
        assertThat(BEAN_VALIDATOR.validate(content("  "))).isNotEmpty();
        assertThat(BEAN_VALIDATOR.validate(content(null))).isNotEmpty();
        assertThat(BEAN_VALIDATOR.validate(content("a".repeat(20_001)))).isNotEmpty();
        assertThat(BEAN_VALIDATOR.validate(content("a".repeat(20_000)))).isEmpty();
    }
}
