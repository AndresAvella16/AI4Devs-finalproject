package com.jobpilot.tracker.web;

import com.jobpilot.tracker.domain.ApplicationStatus;
import com.jobpilot.tracker.service.ApplicationCard;
import java.time.LocalDate;
import java.util.UUID;

/** {@code ApplicationResponse} (sección 4): una tarjeta del tablero. */
public record ApplicationResponse(UUID id, UUID offerId, String offerTitle, String company, Integer score,
        ApplicationStatus status, int boardPosition, LocalDate appliedAt, LocalDate followUpDate,
        boolean followUpOverdue, String notes, long version) {

    static ApplicationResponse from(ApplicationCard card) {
        return new ApplicationResponse(card.id(), card.offerId(), card.offerTitle(), card.company(), card.score(),
                card.status(), card.boardPosition(), card.appliedAt(), card.followUpDate(), card.followUpOverdue(),
                card.notes(), card.version());
    }
}
