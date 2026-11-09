package com.jobpilot.analysis.web;

import com.jobpilot.analysis.service.MatchAnalysisService;
import com.jobpilot.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Análisis de encaje entre el perfil del usuario y una de sus ofertas (F3). */
@Tag(name = "Analysis")
@RestController
@RequestMapping("/api/v1/offers/{offerId}/analysis")
public class AnalysisController {

    private final MatchAnalysisService analyses;

    public AnalysisController(MatchAnalysisService analyses) {
        this.analyses = analyses;
    }

    @Operation(summary = "Calcula el análisis de encaje entre el perfil del usuario y la oferta",
            description = "La IA clasifica cada requisito (CUMPLE / PARCIAL / NO_CUMPLE) con su evidencia y propone "
                    + "3–5 sugerencias; la puntuación la calcula el backend con la fórmula documentada. 404 si la "
                    + "oferta no existe o es de otro usuario; 409 profile-required si el usuario no tiene perfil y "
                    + "offer-not-extracted si la oferta no tiene requisitos extraídos; 429 / 502 / 503 / 504 si "
                    + "falla la IA. Se guarda un histórico: cada llamada crea un análisis nuevo.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID offerId) {
        return AnalysisResponse.from(analyses.analyze(user.userId(), offerId));
    }

    @Operation(summary = "Último análisis de encaje de la oferta",
            description = "404 si la oferta no existe, es de otro usuario o todavía no tiene ningún análisis.")
    @GetMapping
    public AnalysisResponse latest(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID offerId) {
        return AnalysisResponse.from(analyses.latest(user.userId(), offerId));
    }
}
