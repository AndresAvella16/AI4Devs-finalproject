package com.jobpilot.tracker.service;

import com.jobpilot.tracker.domain.Application;
import com.jobpilot.tracker.domain.ApplicationStatus;
import com.jobpilot.tracker.repository.ApplicationBoardRow;
import java.time.LocalDate;
import java.util.UUID;

/** Tarjeta del tablero con {@code followUpOverdue} ya calculado para "hoy". */
public record ApplicationCard(UUID id, UUID offerId, String offerTitle, String company, Integer score,
        ApplicationStatus status, int boardPosition, LocalDate appliedAt, LocalDate followUpDate,
        boolean followUpOverdue, String notes, long version) {

    static ApplicationCard from(ApplicationBoardRow row, LocalDate today) {
        ApplicationStatus status = ApplicationStatus.valueOf(row.getStatus());
        return new ApplicationCard(row.getId(), row.getOfferId(), row.getOfferTitle(), row.getCompany(),
                row.getScore(), status, row.getBoardPosition(), row.getAppliedAt(), row.getFollowUpDate(),
                Application.isFollowUpOverdue(status, row.getFollowUpDate(), today), row.getNotes(),
                row.getVersion());
    }
}
