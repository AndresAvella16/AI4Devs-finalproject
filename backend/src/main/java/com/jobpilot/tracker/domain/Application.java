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

    /** Fila del historial que registra la creación ({@code from_status} NULL). */
    public ApplicationStatusHistory creationHistory() {
        return new ApplicationStatusHistory(this, null, status, createdAt);
    }

    /**
     * Cambia de columna. Al pasar a ENVIADA, la fecha de envío pasa a ser hoy si estaba vacía (HU-05). La transición
     * ya la ha validado {@link ApplicationStatusPolicy}.
     *
     * @return la fila del historial que registra el cambio
     */
    public ApplicationStatusHistory changeStatus(ApplicationStatus to, LocalDate today, Instant now) {
        ApplicationStatus from = status;
        status = to;
        if (to == ApplicationStatus.ENVIADA && appliedAt == null) {
            appliedAt = today;
        }
        updatedAt = now;
        return new ApplicationStatusHistory(this, from, to, now);
    }

    public void editNotes(String notes, Instant now) {
        this.notes = notes;
        updatedAt = now;
    }

    /** Vaciarla solo es válido si el estado no la exige; lo comprueba el servicio antes (→ 400). */
    public void editAppliedAt(LocalDate appliedAt, Instant now) {
        this.appliedAt = appliedAt;
        updatedAt = now;
    }

    public void editFollowUpDate(LocalDate followUpDate, Instant now) {
        this.followUpDate = followUpDate;
        updatedAt = now;
    }

    public void touch(Instant now) {
        updatedAt = now;
    }

    /** Solo lo usa {@link BoardColumn}, que mantiene las posiciones de la columna de 0 a n−1. */
    void moveTo(int position) {
        boardPosition = position;
    }

    /**
     * Seguimiento vencido (HU-05): la fecha de seguimiento es anterior a hoy y la candidatura no está cerrada
     * (OFERTA o DESCARTADA). No se guarda; se calcula al consultar.
     */
    public static boolean isFollowUpOverdue(ApplicationStatus status, LocalDate followUpDate, LocalDate today) {
        return followUpDate != null && followUpDate.isBefore(today) && !status.isClosed();
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
