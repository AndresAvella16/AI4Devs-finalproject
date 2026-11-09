package com.jobpilot.tracker.service;

import com.jobpilot.offer.repository.JobOfferRepository;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.InvalidRequestException;
import com.jobpilot.shared.error.NotFoundException;
import com.jobpilot.tracker.domain.Application;
import com.jobpilot.tracker.domain.ApplicationStatus;
import com.jobpilot.tracker.domain.ApplicationStatusPolicy;
import com.jobpilot.tracker.domain.BoardColumn;
import com.jobpilot.tracker.repository.ApplicationRepository;
import com.jobpilot.tracker.repository.ApplicationStatusHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tablero de candidaturas (F5, HU-05). Aquí no interviene el LLM, así que cada operación es una sola transacción
 * corta. {@code boardPosition} es el índice de la tarjeta en su columna: al colocar una tarjeta se renumera de 0 a
 * n−1 la columna de destino y, si cambia de columna, también la de origen ({@link BoardColumn}).
 *
 * <p>Comprobaciones, en este orden: 404 (no existe o es de otro usuario) → 400 (mismo estado; datos que el estado no
 * admite) → 409 {@code version-conflict} (versión antigua) → 409 {@code invalid-status-transition}.
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final ApplicationStatusHistoryRepository history;
    private final JobOfferRepository offers;
    private final Clock clock;

    ApplicationService(ApplicationRepository applications, ApplicationStatusHistoryRepository history,
            JobOfferRepository offers, Clock clock) {
        this.applications = applications;
        this.history = history;
        this.offers = offers;
        this.clock = clock;
    }

    /** {@code GET /applications}: el tablero completo, en el orden de las columnas. */
    @Transactional(readOnly = true)
    public List<ApplicationCard> list(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        return applications.findBoard(userId).stream().map(row -> ApplicationCard.from(row, today)).toList();
    }

    /** {@code POST /applications}: guarda la oferta en el tablero, al final de la columna GUARDADA. */
    @Transactional
    public ApplicationCard create(UUID userId, UUID offerId) {
        offers.findByIdAndUserId(offerId, userId).orElseThrow(() -> new NotFoundException("No existe la oferta."));
        if (applications.existsByOfferId(offerId)) {
            throw alreadyExists();
        }
        int position = applications.nextPosition(userId, ApplicationStatus.GUARDADA);
        Application application = new Application(userId, offerId, ApplicationStatus.GUARDADA, position, null, null,
                null, clock.instant());
        try {
            applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            // Otra petición ha guardado la misma oferta a la vez (uq_applications_offer).
            throw alreadyExists();
        }
        history.save(application.creationHistory());
        return card(userId, application.getId());
    }

    /** {@code PATCH /applications/{id}}: notas, fechas y orden dentro de la columna. No toca el historial. */
    @Transactional
    public ApplicationCard update(UUID userId, UUID applicationId, ApplicationChanges changes) {
        Application application = find(userId, applicationId);
        if (changes.appliedAt() != null && changes.appliedAt().isClear()
                && application.getStatus().requiresAppliedAt()) {
            throw new InvalidRequestException("appliedAt", "La fecha de envío es obligatoria en una candidatura "
                    + application.getStatus() + ".");
        }
        checkVersion(application, changes.version());
        Instant now = clock.instant();
        if (changes.notes() != null) {
            application.editNotes(changes.notes().value(), now);
        }
        if (changes.appliedAt() != null) {
            application.editAppliedAt(changes.appliedAt().value(), now);
        }
        if (changes.followUpDate() != null) {
            application.editFollowUpDate(changes.followUpDate().value(), now);
        }
        if (changes.boardPosition() != null) {
            BoardColumn.place(applications.findColumn(userId, application.getStatus()), application,
                    changes.boardPosition());
            application.touch(now);
        }
        return card(userId, applicationId);
    }

    /** {@code PATCH /applications/{id}/status}: cambia de columna según {@link ApplicationStatusPolicy}. */
    @Transactional
    public ApplicationCard changeStatus(UUID userId, UUID applicationId, ApplicationStatus to, int boardPosition,
            long version) {
        Application application = find(userId, applicationId);
        ApplicationStatus from = application.getStatus();
        if (from == to) {
            throw new InvalidRequestException("status", "La candidatura ya está en " + to
                    + ". Para reordenarla dentro de la columna usa PATCH /applications/{applicationId}.");
        }
        checkVersion(application, version);
        if (!ApplicationStatusPolicy.isAllowed(from, to)) {
            throw new ConflictException("invalid-status-transition", "Transición de estado no permitida",
                    "No se puede pasar de " + from + " a " + to + ".");
        }
        List<Application> origin = applications.findColumn(userId, from);
        List<Application> destination = applications.findColumn(userId, to);
        history.save(application.changeStatus(to, LocalDate.now(clock), clock.instant()));
        BoardColumn.compact(origin.stream().filter(other -> !other.getId().equals(applicationId)).toList());
        BoardColumn.place(destination, application, boardPosition);
        return card(userId, applicationId);
    }

    private Application find(UUID userId, UUID applicationId) {
        return applications.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new NotFoundException("No existe la candidatura."));
    }

    /**
     * La entidad se acaba de leer, así que {@code @Version} solo detectaría una carrera entre dos peticiones; la
     * versión que el cliente vio se compara aquí.
     */
    private static void checkVersion(Application application, long version) {
        if (application.getVersion() == null || application.getVersion() != version) {
            throw ConflictException.versionConflict();
        }
    }

    /** Relee la tarjeta con los datos de la oferta (la consulta nativa vuelca antes los cambios pendientes). */
    private ApplicationCard card(UUID userId, UUID applicationId) {
        return applications.findBoardRow(userId, applicationId)
                .map(row -> ApplicationCard.from(row, LocalDate.now(clock)))
                .orElseThrow(() -> new IllegalStateException("La candidatura " + applicationId + " no se ha leído"));
    }

    private static ConflictException alreadyExists() {
        return new ConflictException("application-already-exists", "La oferta ya está en el tablero",
                "Esta oferta ya tiene una candidatura en el tablero.");
    }
}
