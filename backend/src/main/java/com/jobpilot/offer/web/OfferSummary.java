package com.jobpilot.offer.web;

import com.jobpilot.offer.domain.ExtractionStatus;
import com.jobpilot.offer.repository.OfferSummaryRow;
import com.jobpilot.tracker.domain.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

/** Elemento del dashboard ({@code GET /offers}, schema {@code OfferSummary} de la sección 4). */
public record OfferSummary(
        UUID id,
        String title,
        String company,
        Instant createdAt,
        ExtractionStatus extractionStatus,
        Integer latestScore,
        Boolean hasBlockingGap,
        ApplicationStatus applicationStatus) {

    static OfferSummary from(OfferSummaryRow row) {
        return new OfferSummary(row.getId(), row.getTitle(), row.getCompany(), row.getCreatedAt(),
                ExtractionStatus.valueOf(row.getExtractionStatus()), row.getLatestScore(), row.getHasBlockingGap(),
                row.getApplicationStatus() == null ? null : ApplicationStatus.valueOf(row.getApplicationStatus()));
    }
}
