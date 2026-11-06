package com.jobpilot.tracker.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Cambio de estado de una candidatura (tabla {@code application_status_history}). {@code fromStatus} null = creación. */
@Entity
@Table(name = "application_status_history")
public class ApplicationStatusHistory {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private Application application;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus toStatus;

    private Instant changedAt;

    protected ApplicationStatusHistory() {
    }

    public ApplicationStatusHistory(Application application, ApplicationStatus fromStatus, ApplicationStatus toStatus,
            Instant changedAt) {
        this.id = UUID.randomUUID();
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
