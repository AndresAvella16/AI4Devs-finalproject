package com.jobpilot.tracker.web;

import com.jobpilot.tracker.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** {@code ChangeStatusRequest} (sección 4): columna de destino, índice dentro de ella y versión actual. */
public record ChangeStatusRequest(
        @NotNull ApplicationStatus status,
        @Schema(description = "Índice en la columna de destino; si es mayor que su tamaño, la tarjeta queda al final")
        @NotNull @PositiveOrZero Integer boardPosition,
        @Schema(description = "Versión actual (bloqueo optimista)")
        @NotNull Long version) {
}
