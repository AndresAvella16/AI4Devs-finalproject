package com.jobpilot.offer.web;

import com.jobpilot.offer.service.OfferDetails;
import com.jobpilot.offer.service.OfferService;
import com.jobpilot.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ofertas del usuario autenticado (F2). Una oferta de otro usuario no existe para él (→ 404). */
@RestController
@RequestMapping("/api/v1/offers")
public class OfferController {

    private final OfferService offers;

    public OfferController(OfferService offers) {
        this.offers = offers;
    }

    @Operation(summary = "Importa una oferta y extrae sus requisitos con IA",
            description = "El texto se guarda siempre. Si la IA falla, responde igualmente 201 con extractionStatus "
                    + "FALLIDA y se puede reintentar con POST /offers/{offerId}/extraction. 429 si se supera el "
                    + "límite de peticiones de IA.")
    @PostMapping
    public ResponseEntity<OfferResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateOfferRequest request) {
        OfferDetails created = offers.create(user.userId(), request.rawText(), request.sourceUrl());
        return ResponseEntity.created(URI.create("/api/v1/offers/" + created.offer().getId()))
                .body(OfferResponse.from(created));
    }

    @Operation(summary = "Reintenta la extracción de una oferta PENDIENTE o FALLIDA",
            description = "Sustituye los requisitos y análisis anteriores en una sola transacción. 409 si la "
                    + "extracción ya está COMPLETADA; 429 / 502 / 503 / 504 si falla la IA.")
    @PostMapping("/{offerId}/extraction")
    public OfferResponse retryExtraction(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID offerId) {
        return OfferResponse.from(offers.retryExtraction(user.userId(), offerId));
    }

    @Operation(summary = "Dashboard: ofertas del usuario, de la más reciente a la más antigua",
            description = "Con la puntuación del último análisis y el estado de la candidatura, si los hay.")
    @GetMapping
    public List<OfferSummary> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return offers.list(user.userId()).stream().map(OfferSummary::from).toList();
    }

    @Operation(summary = "Detalle de una oferta con sus requisitos")
    @GetMapping("/{offerId}")
    public OfferResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID offerId) {
        return OfferResponse.from(offers.get(user.userId(), offerId));
    }
}
