package com.jobpilot.tracker.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Candidatura en el tablero (tabla {@code applications}). Una por oferta y siempre del propietario de la oferta
 * (FK compuesta). {@code version} es el bloqueo optimista de {@code PATCH /applications/{id}}.
 */
@Entity
@Table(name = "applications")
public class Application {

    @Id
    private UUID id;

    private UUID userId;

    private UUID offerId;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    private int boardPosition;

    private LocalDate appliedAt;

    private LocalDate followUpDate;

    private String notes;

    @Version
    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    protected Application() {
    }

    public Application(UUID userId, UUID offerId, ApplicationStatus status, int boardPosition, LocalDate appliedAt,
            LocalDate followUpDate, String notes, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.offerId = offerId;
        this.status = status;
        this.boardPosition = boardPosition;
        this.appliedAt = appliedAt;
        this.followUpDate = followUpDate;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOfferId() {
        return offerId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public int getBoardPosition() {
        return boardPosition;
    }

    public LocalDate getAppliedAt() {
        return appliedAt;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public String getNotes() {
        return notes;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
