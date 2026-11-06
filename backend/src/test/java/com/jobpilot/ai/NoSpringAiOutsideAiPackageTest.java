package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** CA 3: Spring AI y el SDK de Anthropic solo se usan dentro de {@code com.jobpilot.ai} (CLAUDE.md, reglas de IA). */
class NoSpringAiOutsideAiPackageTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Path AI_PACKAGE = MAIN_SOURCES.resolve("com/jobpilot/ai");

    private static final Pattern FORBIDDEN =
            Pattern.compile("^\\s*import\\s+(static\\s+)?(org\\.springframework\\.ai|com\\.anthropic)\\.",
                    Pattern.MULTILINE);

    @Test
    void ningunFicheroFueraDelPaqueteAiImportaSpringAiNiElSdkDeAnthropic() throws IOException {
        assertThat(MAIN_SOURCES).isDirectory();
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            offenders = files
                    .filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !file.startsWith(AI_PACKAGE))
                    .filter(NoSpringAiOutsideAiPackageTest::importsForbidden)
                    .toList();
        }

        assertThat(offenders).as("ficheros fuera de com.jobpilot.ai que importan Spring AI o Anthropic").isEmpty();
    }

    @Test
    void elPatronDetectaLasImportacionesProhibidas() {
        assertThat(FORBIDDEN.matcher("package x;\nimport org.springframework.ai.chat.model.ChatModel;").find()).isTrue();
        assertThat(FORBIDDEN.matcher("import static com.anthropic.core.JsonValue.from;").find()).isTrue();
        assertThat(FORBIDDEN.matcher("import org.springframework.aop.Advice;").find()).isFalse();
    }

    private static boolean importsForbidden(Path file) {
        try {
            return FORBIDDEN.matcher(Files.readString(file, StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            throw new IllegalStateException("No se puede leer " + file, e);
        }
    }
}
