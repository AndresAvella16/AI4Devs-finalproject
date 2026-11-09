package com.jobpilot.tracker.web;

import com.jobpilot.shared.security.AuthenticatedUser;
import com.jobpilot.tracker.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tablero de candidaturas del usuario autenticado (F5). Una candidatura de otro usuario no existe para él (→ 404). */
@Tag(name = "Applications")
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applications;

    public ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @Operation(summary = "Tablero: candidaturas del usuario",
            description = "En el orden de las columnas (GUARDADA, ENVIADA, ENTREVISTA, OFERTA, DESCARTADA) y, dentro "
                    + "de cada una, por boardPosition. Con la puntuación del último análisis y followUpOverdue.")
    @GetMapping
    public List<ApplicationResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return applications.list(user.userId()).stream().map(ApplicationResponse::from).toList();
    }

    @Operation(summary = "Guarda una oferta en el tablero",
            description = "Crea la candidatura en GUARDADA, al final de la columna, y lo registra en el historial. "
                    + "404 si la oferta no existe o es de otro usuario; 409 application-already-exists si ya está "
                    + "en el tablero.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateApplicationRequest request) {
        return ApplicationResponse.from(applications.create(user.userId(), request.offerId()));
    }

    @Operation(summary = "Edita notas y fechas o reordena la tarjeta dentro de su columna",
            description = "Solo se modifican los campos enviados; null borra el valor. boardPosition es el índice en "
                    + "la columna (la columna se renumera). No cambia el estado ni el historial. 400 si se vacía "
                    + "appliedAt en ENVIADA, ENTREVISTA u OFERTA; 409 version-conflict si la versión es antigua.")
    @PatchMapping("/{applicationId}")
    public ApplicationResponse update(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID applicationId, @Valid @RequestBody UpdateApplicationRequest request) {
        return ApplicationResponse.from(applications.update(user.userId(), applicationId, request.toChanges()));
    }

    @Operation(summary = "Mueve una candidatura a otra columna del tablero",
            description = "Aplica las reglas de transición de la HU-05 y registra el cambio en el historial. Al pasar "
                    + "a ENVIADA, appliedAt pasa a ser hoy si estaba vacía. 400 si el estado es el actual (para "
                    + "reordenar se usa PATCH /applications/{applicationId}); 409 invalid-status-transition o "
                    + "version-conflict.")
    @PatchMapping("/{applicationId}/status")
    public ApplicationResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID applicationId, @Valid @RequestBody ChangeStatusRequest request) {
        return ApplicationResponse.from(applications.changeStatus(user.userId(), applicationId, request.status(),
                request.boardPosition(), request.version()));
    }
}
