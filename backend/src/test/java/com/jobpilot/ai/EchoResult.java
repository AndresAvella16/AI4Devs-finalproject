package com.jobpilot.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Respuesta estructurada de prueba, con las mismas anotaciones que llevarán las respuestas reales. */
record EchoResult(@NotBlank String answer, @NotNull @Size(min = 1, max = 3) List<@NotBlank String> items) {
}
