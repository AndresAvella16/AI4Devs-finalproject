package com.jobpilot.tracker.web;

import com.jobpilot.shared.web.FieldUpdate;
import com.jobpilot.tracker.service.ApplicationChanges;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code UpdateApplicationRequest} (sección 4). Campos opcionales; solo se modifican los que se envían. Un campo
 * ausente llega como {@code null} y uno enviado a {@code null} como {@link FieldUpdate#clear()}, que borra el valor.
 */
@Schema(description = "Campos opcionales; solo se modifican los que se envían (null borra el valor). Sirve también "
        + "para reordenar dentro de la columna.")
public record UpdateApplicationRequest(
        @Schema(implementation = String.class, nullable = true, maxLength = 5000)
        FieldUpdate<@Size(max = 5000) String> notes,
        @Schema(implementation = LocalDate.class, nullable = true)
        FieldUpdate<LocalDate> appliedAt,
        @Schema(implementation = LocalDate.class, nullable = true)
        FieldUpdate<LocalDate> followUpDate,
        @PositiveOrZero Integer boardPosition,
        @Schema(description = "Versión actual (bloqueo optimista)")
        @NotNull Long version) {

    ApplicationChanges toChanges() {
        return new ApplicationChanges(notes, appliedAt, followUpDate, boardPosition, version);
    }
}
