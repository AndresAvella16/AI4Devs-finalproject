package com.jobpilot.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Política de la sección 2.5: mínimo 8 caracteres, al menos una letra y un número, máximo 72 bytes UTF-8 (BCrypt). */
class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    /** Contraseña con letras y un número que ocupa exactamente {@code bytes} bytes en UTF-8 (solo ASCII). */
    private static String asciiOfBytes(int bytes) {
        return "a".repeat(bytes - 1) + "1";
    }

    static Stream<String> valid() {
        return Stream.of(
                "Demo1234!",
                "abcdefg1",                       // 8 caracteres justos
                "1234567a",
                "contraseña1",                    // letras no ASCII
                "frase larga con espacios 2026",
                asciiOfBytes(72));                // límite de BCrypt
    }

    static Stream<String> invalid() {
        return Stream.of(
                "",
                "abc123",                         // menos de 8
                "abcdefg",                        // 7, sin números
                "abcdefgh",                       // sin números
                "12345678",                       // sin letras
                "!!!!!!!!",                       // ni letras ni números
                "        ",
                asciiOfBytes(73),                 // un byte de más
                "ñ".repeat(36) + "1");            // 37 caracteres pero 73 bytes: "ñ" ocupa 2 bytes
    }

    @ParameterizedTest
    @MethodSource("valid")
    void aceptaContrasenasQueCumplenLaPolitica(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalid")
    void rechazaContrasenasQueNoCumplenLaPolitica(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void losCasosLimiteMidenLoQueDicen() {
        assertThat(asciiOfBytes(72).getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(("ñ".repeat(36) + "1")).hasSize(37);
        assertThat(("ñ".repeat(36) + "1").getBytes(StandardCharsets.UTF_8)).hasSize(73);
    }

    @Test
    void nullLoDejaParaNotNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
