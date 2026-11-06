package com.jobpilot.offer.service;

import com.jobpilot.ai.OfferExtraction;
import com.jobpilot.analysis.repository.MatchAnalysisRepository;
import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.domain.OfferRequirement;
import com.jobpilot.offer.repository.JobOfferRepository;
import com.jobpilot.offer.repository.OfferRequirementRepository;
import com.jobpilot.shared.error.NotFoundException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las transacciones cortas de la importación. Están en su propio bean para que {@link OfferService}, que llama al
 * LLM entre una y otra, no tenga ninguna transacción abierta mientras espera (CLAUDE.md).
 */
@Component
class OfferPersistence {

    private final JobOfferRepository offers;
    private final OfferRequirementRepository requirements;
    private final MatchAnalysisRepository analyses;
    private final Clock clock;

    OfferPersistence(JobOfferRepository offers, OfferRequirementRepository requirements,
            MatchAnalysisRepository analyses, Clock clock) {
        this.offers = offers;
        this.requirements = requirements;
        this.analyses = analyses;
        this.clock = clock;
    }

    /** Guarda el texto antes de llamar al LLM: pase lo que pase después, no se pierde. */
    @Transactional
    JobOffer createPending(UUID userId, String rawText, String sourceUrl) {
        return offers.save(JobOffer.pending(userId, rawText, sourceUrl, clock.instant()));
    }

    /** La extracción ha fallado: la oferta queda FALLIDA (salvo que otra petición la haya completado ya). */
    @Transactional
    OfferDetails markFailed(UUID offerId) {
        JobOffer offer = lock(offerId);
        offer.failExtraction();
        return new OfferDetails(offer, requirements.findByOfferIdOrdered(offerId));
    }

    /**
     * Guarda la extracción. Borra antes los requisitos y análisis anteriores de la oferta en esta misma
     * transacción, para que ningún análisis quede con el detalle a medias (sección 3.2).
     *
     * @throws com.jobpilot.shared.error.ConflictException si otra petición la ha completado mientras se esperaba
     *         al LLM
     */
    @Transactional
    OfferDetails complete(UUID offerId, OfferExtraction extraction) {
        JobOffer offer = lock(offerId);
        if (offer.isExtractionCompleted()) {
            throw OfferService.alreadyExtracted();
        }
        analyses.deleteByOfferId(offerId);
        requirements.deleteByOfferId(offerId);

        OfferExtraction.Salary salary = extraction.salary();
        offer.completeExtraction(extraction.title().strip(), blankToNull(extraction.company()),
                blankToNull(extraction.location()), extraction.workMode(),
                salary == null ? null : salary.min(), salary == null ? null : salary.max(),
                salary == null ? null : salary.currency());

        List<OfferRequirement> created = new ArrayList<>();
        short order = 0;
        for (OfferExtraction.Requirement requirement : extraction.requirements()) {
            created.add(new OfferRequirement(offer, requirement.description().strip(), requirement.type(),
                    requirement.category(), order++));
        }
        return new OfferDetails(offer, requirements.saveAll(created));
    }

    @Transactional(readOnly = true)
    OfferDetails details(UUID userId, UUID offerId) {
        JobOffer offer = offers.findByIdAndUserId(offerId, userId).orElseThrow(OfferService::offerNotFound);
        return new OfferDetails(offer, requirements.findByOfferIdOrdered(offerId));
    }

    private JobOffer lock(UUID offerId) {
        return offers.findByIdForUpdate(offerId)
                .orElseThrow(() -> new NotFoundException("La oferta ya no existe."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
