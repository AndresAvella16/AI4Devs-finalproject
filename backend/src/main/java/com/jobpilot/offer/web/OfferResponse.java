package com.jobpilot.offer.web;

import com.jobpilot.offer.domain.ExtractionStatus;
import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.domain.OfferRequirement;
import com.jobpilot.offer.domain.RequirementCategory;
import com.jobpilot.offer.domain.RequirementType;
import com.jobpilot.offer.domain.WorkMode;
import com.jobpilot.offer.service.OfferDetails;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Schema {@code OfferResponse} de la sección 4. Con la extracción PENDIENTE o FALLIDA, {@code title},
 * {@code company}, {@code location} y {@code salary} son {@code null}, {@code workMode} es {@code DESCONOCIDO} y no
 * hay requisitos.
 */
public record OfferResponse(
        UUID id,
        String title,
        String company,
        String location,
        WorkMode workMode,
        Salary salary,
        String sourceUrl,
        ExtractionStatus extractionStatus,
        List<Requirement> requirements,
        Instant createdAt) {

    /** {@code min} y {@code max} pueden faltar por separado; {@code currency} siempre tiene valor. */
    public record Salary(Integer min, Integer max, String currency) {
    }

    public record Requirement(UUID id, String description, RequirementType type, RequirementCategory category) {

        static Requirement from(OfferRequirement requirement) {
            return new Requirement(requirement.getId(), requirement.getDescription(), requirement.getType(),
                    requirement.getCategory());
        }
    }

    static OfferResponse from(OfferDetails details) {
        JobOffer offer = details.offer();
        Salary salary = offer.getSalaryMin() == null && offer.getSalaryMax() == null
                ? null
                : new Salary(offer.getSalaryMin(), offer.getSalaryMax(), offer.getCurrency());
        return new OfferResponse(offer.getId(), offer.getTitle(), offer.getCompany(), offer.getLocation(),
                offer.getWorkMode(), salary, offer.getSourceUrl(), offer.getExtractionStatus(),
                details.requirements().stream().map(Requirement::from).toList(), offer.getCreatedAt());
    }
}
