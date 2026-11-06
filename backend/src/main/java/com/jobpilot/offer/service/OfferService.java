package com.jobpilot.offer.service;

import com.jobpilot.ai.LlmGateway;
import com.jobpilot.ai.OfferExtraction;
import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.repository.JobOfferRepository;
import com.jobpilot.offer.repository.OfferSummaryRow;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.LlmInvalidResponseException;
import com.jobpilot.shared.error.LlmOverloadedException;
import com.jobpilot.shared.error.LlmTimeoutException;
import com.jobpilot.shared.error.NotFoundException;
import com.jobpilot.shared.ratelimit.RateLimits;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Importación de ofertas (F2, HU-02). Sin {@code @Transactional} a propósito: el LLM tarda segundos y nunca se le
 * espera con una transacción abierta. El flujo es:
 *
 * <ol>
 *   <li>Se gasta una petición del cupo de IA del usuario (si no queda → 429 y no se guarda nada).</li>
 *   <li>Transacción corta: la oferta se guarda en PENDIENTE con su texto.</li>
 *   <li>Llamada al LLM, fuera de transacción.</li>
 *   <li>Transacción corta: COMPLETADA con sus requisitos, o FALLIDA si la IA ha fallado.</li>
 * </ol>
 *
 * <p>Un error que no es de IA (un fallo de programación, una API key mala...) se propaga como 500, pero la oferta
 * ya está guardada en PENDIENTE y se puede reintentar.
 */
@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    private final JobOfferRepository offers;
    private final OfferPersistence persistence;
    private final LlmGateway llm;
    private final RateLimits rateLimits;

    OfferService(JobOfferRepository offers, OfferPersistence persistence, LlmGateway llm, RateLimits rateLimits) {
        this.offers = offers;
        this.persistence = persistence;
        this.llm = llm;
        this.rateLimits = rateLimits;
    }

    /** {@code POST /offers}: si la IA falla, devuelve la oferta en FALLIDA en lugar de lanzar el error. */
    public OfferDetails create(UUID userId, String rawText, String sourceUrl) {
        rateLimits.consumeAiCall(userId);
        JobOffer offer = persistence.createPending(userId, rawText, blankToNull(sourceUrl));
        try {
            return persistence.complete(offer.getId(), llm.extractOffer(rawText));
        } catch (LlmTimeoutException | LlmOverloadedException | LlmInvalidResponseException e) {
            log.warn("Extracción fallida de la oferta {} ({}); queda en FALLIDA", offer.getId(),
                    e.getClass().getSimpleName());
            return persistence.markFailed(offer.getId());
        }
    }

    /**
     * {@code POST /offers/{offerId}/extraction}: reintenta la extracción de una oferta PENDIENTE o FALLIDA. Si la IA
     * vuelve a fallar, la oferta queda FALLIDA y el error se propaga (→ 502 / 503 / 504).
     */
    public OfferDetails retryExtraction(UUID userId, UUID offerId) {
        JobOffer offer = offers.findByIdAndUserId(offerId, userId).orElseThrow(OfferService::offerNotFound);
        if (offer.isExtractionCompleted()) {
            throw alreadyExtracted();
        }
        rateLimits.consumeAiCall(userId);
        OfferExtraction extraction;
        try {
            extraction = llm.extractOffer(offer.getRawText());
        } catch (LlmTimeoutException | LlmOverloadedException | LlmInvalidResponseException e) {
            persistence.markFailed(offerId);
            throw e;
        }
        return persistence.complete(offerId, extraction);
    }

    public OfferDetails get(UUID userId, UUID offerId) {
        return persistence.details(userId, offerId);
    }

    public List<OfferSummaryRow> list(UUID userId) {
        return offers.findSummariesByUserId(userId);
    }

    static NotFoundException offerNotFound() {
        return new NotFoundException("No existe la oferta.");
    }

    static ConflictException alreadyExtracted() {
        return new ConflictException("offer-already-extracted", "La oferta ya tiene sus requisitos extraídos",
                "La extracción de esta oferta ya se completó; no se puede repetir.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
