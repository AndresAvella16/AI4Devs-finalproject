package com.jobpilot.tracker.domain;

import static com.jobpilot.tracker.domain.ApplicationStatus.DESCARTADA;
import static com.jobpilot.tracker.domain.ApplicationStatus.ENTREVISTA;
import static com.jobpilot.tracker.domain.ApplicationStatus.ENVIADA;
import static com.jobpilot.tracker.domain.ApplicationStatus.GUARDADA;
import static com.jobpilot.tracker.domain.ApplicationStatus.OFERTA;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Reglas de transición de la HU-05: la matriz completa de 5 × 5 estados. */
class ApplicationStatusPolicyTest {

    /** Lo único permitido: avanzar un paso, descartar desde cualquier estado no descartado y reabrir. */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = Map.of(
            GUARDADA, EnumSet.of(ENVIADA, DESCARTADA),
            ENVIADA, EnumSet.of(ENTREVISTA, DESCARTADA),
            ENTREVISTA, EnumSet.of(OFERTA, DESCARTADA),
            OFERTA, EnumSet.of(DESCARTADA),
            DESCARTADA, EnumSet.of(GUARDADA));

    static Stream<Arguments> allPairs() {
        return Stream.of(ApplicationStatus.values())
                .flatMap(from -> Stream.of(ApplicationStatus.values())
                        .map(to -> Arguments.of(from, to, ALLOWED.get(from).contains(to))));
    }

    @ParameterizedTest(name = "{0} → {1}: {2}")
    @MethodSource("allPairs")
    void allowsOnlyTheTransitionsOfTheStory(ApplicationStatus from, ApplicationStatus to, boolean expected) {
        assertThat(ApplicationStatusPolicy.isAllowed(from, to)).isEqualTo(expected);
    }

    static Stream<Arguments> namedCases() {
        return Stream.of(
                Arguments.of(GUARDADA, ENVIADA, true),
                Arguments.of(ENTREVISTA, OFERTA, true),
                Arguments.of(OFERTA, DESCARTADA, true),
                Arguments.of(DESCARTADA, GUARDADA, true),
                Arguments.of(OFERTA, GUARDADA, false),    // retroceso (escenario de la HU-05)
                Arguments.of(OFERTA, ENVIADA, false),     // retroceso (Ejemplo 3 de la sección 4)
                Arguments.of(GUARDADA, OFERTA, false),    // salto
                Arguments.of(DESCARTADA, ENVIADA, false), // reabrir solo a GUARDADA
                Arguments.of(ENVIADA, ENVIADA, false));   // mismo estado
    }

    @ParameterizedTest(name = "{0} → {1}: {2}")
    @MethodSource("namedCases")
    void namedCasesFromTheReadme(ApplicationStatus from, ApplicationStatus to, boolean expected) {
        assertThat(ApplicationStatusPolicy.isAllowed(from, to)).isEqualTo(expected);
    }
}
