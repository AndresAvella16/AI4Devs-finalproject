package com.jobpilot.tracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.repository.JobOfferRepository;
import com.jobpilot.shared.config.ClockConfig;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.InvalidRequestException;
import com.jobpilot.shared.error.NotFoundException;
import com.jobpilot.shared.web.FieldUpdate;
import com.jobpilot.tracker.domain.Application;
import com.jobpilot.tracker.domain.ApplicationStatus;
import com.jobpilot.tracker.domain.ApplicationStatusHistory;
import com.jobpilot.tracker.repository.ApplicationBoardRow;
import com.jobpilot.tracker.repository.ApplicationRepository;
import com.jobpilot.tracker.repository.ApplicationStatusHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T09:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static final UUID USER = UUID.randomUUID();

    private ApplicationRepository applications;
    private ApplicationStatusHistoryRepository history;
    private JobOfferRepository offers;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        history = mock(ApplicationStatusHistoryRepository.class);
        offers = mock(JobOfferRepository.class);
        service = new ApplicationService(applications, history, offers, Clock.fixed(NOW, ClockConfig.ZONE));
        ApplicationBoardRow row = row();
        when(applications.findBoardRow(any(), any())).thenReturn(Optional.of(row));
    }

    private static Application application(ApplicationStatus status, long version) {
        LocalDate appliedAt = status.requiresAppliedAt() ? TODAY.minusDays(3) : null;
        Application application = new Application(USER, UUID.randomUUID(), status, 0, appliedAt, null, "Notas",
                NOW.minusSeconds(86_400));
        ReflectionTestUtils.setField(application, "version", version);
        return application;
    }

    private Application stored(ApplicationStatus status, long version) {
        Application application = application(status, version);
        when(applications.findByIdAndUserId(application.getId(), USER)).thenReturn(Optional.of(application));
        when(applications.findColumn(USER, status)).thenReturn(new ArrayList<>(List.of(application)));
        return application;
    }

    private static ApplicationBoardRow row() {
        ApplicationBoardRow row = mock(ApplicationBoardRow.class);
        when(row.getStatus()).thenReturn("GUARDADA");
        when(row.getVersion()).thenReturn(0L);
        return row;
    }

    private static ApplicationChanges onlyVersion(long version) {
        return new ApplicationChanges(null, null, null, null, version);
    }

    // ---- POST /applications ----

    @Test
    void createRejectsAnOfferOfAnotherUser() {
        UUID offerId = UUID.randomUUID();
        when(offers.findByIdAndUserId(offerId, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER, offerId)).isInstanceOf(NotFoundException.class);
        verify(applications, never()).saveAndFlush(any());
        verify(history, never()).save(any());
    }

    @Test
    void createRejectsAnOfferAlreadyOnTheBoard() {
        UUID offerId = UUID.randomUUID();
        when(offers.findByIdAndUserId(offerId, USER)).thenReturn(Optional.of(mock(JobOffer.class)));
        when(applications.existsByOfferId(offerId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER, offerId))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getTypeSlug()).isEqualTo("application-already-exists"));
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void createTurnsAConcurrentDuplicateIntoAConflict() {
        UUID offerId = UUID.randomUUID();
        when(offers.findByIdAndUserId(offerId, USER)).thenReturn(Optional.of(mock(JobOffer.class)));
        when(applications.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_applications_offer"));

        assertThatThrownBy(() -> service.create(USER, offerId))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getTypeSlug()).isEqualTo("application-already-exists"));
        verify(history, never()).save(any());
    }

    @Test
    void createPutsTheCardAtTheEndOfGuardadaAndRecordsTheCreation() {
        UUID offerId = UUID.randomUUID();
        when(offers.findByIdAndUserId(offerId, USER)).thenReturn(Optional.of(mock(JobOffer.class)));
        when(applications.nextPosition(USER, ApplicationStatus.GUARDADA)).thenReturn(3);
        when(applications.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(USER, offerId);

        ArgumentCaptor<Application> saved = ArgumentCaptor.forClass(Application.class);
        verify(applications).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ApplicationStatus.GUARDADA);
        assertThat(saved.getValue().getBoardPosition()).isEqualTo(3);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        ArgumentCaptor<ApplicationStatusHistory> entry = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().getFromStatus()).isNull();
        assertThat(entry.getValue().getToStatus()).isEqualTo(ApplicationStatus.GUARDADA);
    }

    // ---- PATCH /applications/{id}/status ----

    @Test
    void changeStatusOfAnotherUsersApplicationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(applications.findByIdAndUserId(id, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(USER, id, ApplicationStatus.ENVIADA, 0, 0))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void sameStatusIsABadRequestBeforeTheVersionCheck() {
        Application application = stored(ApplicationStatus.ENVIADA, 4);

        assertThatThrownBy(() -> service.changeStatus(USER, application.getId(), ApplicationStatus.ENVIADA, 0, 1))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("status"));
        verify(history, never()).save(any());
    }

    @Test
    void staleVersionIsAConflictBeforeThePolicy() {
        Application application = stored(ApplicationStatus.OFERTA, 4);

        assertThatThrownBy(() -> service.changeStatus(USER, application.getId(), ApplicationStatus.GUARDADA, 0, 3))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getTypeSlug()).isEqualTo("version-conflict"));
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.OFERTA);
        verify(history, never()).save(any());
    }

    @Test
    void forbiddenTransitionIsAConflictWithAnExplanation() {
        Application application = stored(ApplicationStatus.OFERTA, 4);

        assertThatThrownBy(() -> service.changeStatus(USER, application.getId(), ApplicationStatus.ENVIADA, 0, 4))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.getTypeSlug()).isEqualTo("invalid-status-transition");
                    assertThat(e.getTitle()).isEqualTo("Transición de estado no permitida");
                    assertThat(e.getMessage()).isEqualTo("No se puede pasar de OFERTA a ENVIADA.");
                });
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.OFERTA);
        verify(history, never()).save(any());
    }

    @Test
    void allowedTransitionMovesTheCardAndRecordsIt() {
        Application application = stored(ApplicationStatus.GUARDADA, 2);
        Application alreadySent = application(ApplicationStatus.ENVIADA, 0);
        when(applications.findColumn(USER, ApplicationStatus.ENVIADA))
                .thenReturn(new ArrayList<>(List.of(alreadySent)));

        service.changeStatus(USER, application.getId(), ApplicationStatus.ENVIADA, 0, 2);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ENVIADA);
        assertThat(application.getAppliedAt()).isEqualTo(TODAY);
        assertThat(application.getBoardPosition()).isZero();
        assertThat(alreadySent.getBoardPosition()).isEqualTo(1);
        ArgumentCaptor<ApplicationStatusHistory> entry = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().getFromStatus()).isEqualTo(ApplicationStatus.GUARDADA);
        assertThat(entry.getValue().getToStatus()).isEqualTo(ApplicationStatus.ENVIADA);
        assertThat(entry.getValue().getChangedAt()).isEqualTo(NOW);
    }

    // ---- PATCH /applications/{id} ----

    @Test
    void updateOfAnotherUsersApplicationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(applications.findByIdAndUserId(id, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER, id, onlyVersion(0))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateWithAStaleVersionIsAConflictAndChangesNothing() {
        Application application = stored(ApplicationStatus.GUARDADA, 5);

        assertThatThrownBy(() -> service.update(USER, application.getId(),
                new ApplicationChanges(FieldUpdate.of("Nuevo"), null, null, null, 4)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getTypeSlug()).isEqualTo("version-conflict"));
        assertThat(application.getNotes()).isEqualTo("Notas");
    }

    @Test
    void updateOnlyTouchesTheFieldsThatAreSent() {
        Application application = stored(ApplicationStatus.GUARDADA, 1);
        LocalDate followUp = TODAY.plusDays(7);

        service.update(USER, application.getId(),
                new ApplicationChanges(null, null, FieldUpdate.of(followUp), null, 1));

        assertThat(application.getNotes()).isEqualTo("Notas");
        assertThat(application.getFollowUpDate()).isEqualTo(followUp);
        assertThat(application.getUpdatedAt()).isEqualTo(NOW);
        verify(history, never()).save(any());
    }

    @Test
    void anExplicitNullClearsTheField() {
        Application application = stored(ApplicationStatus.GUARDADA, 1);

        service.update(USER, application.getId(), new ApplicationChanges(FieldUpdate.clear(), null, null, null, 1));

        assertThat(application.getNotes()).isNull();
    }

    @Test
    void appliedAtCannotBeClearedOnceSent() {
        Application application = stored(ApplicationStatus.ENVIADA, 1);
        LocalDate appliedAt = application.getAppliedAt();

        assertThatThrownBy(() -> service.update(USER, application.getId(),
                new ApplicationChanges(null, FieldUpdate.clear(), null, null, 1)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("appliedAt"));
        assertThat(application.getAppliedAt()).isEqualTo(appliedAt);
    }

    @Test
    void reorderingRenumbersTheColumnWithoutHistory() {
        Application first = application(ApplicationStatus.GUARDADA, 0);
        Application second = application(ApplicationStatus.GUARDADA, 0);
        ReflectionTestUtils.setField(second, "boardPosition", 1);
        when(applications.findByIdAndUserId(second.getId(), USER)).thenReturn(Optional.of(second));
        when(applications.findColumn(USER, ApplicationStatus.GUARDADA))
                .thenReturn(new ArrayList<>(List.of(first, second)));

        service.update(USER, second.getId(), new ApplicationChanges(null, null, null, 0, 0));

        assertThat(second.getBoardPosition()).isZero();
        assertThat(first.getBoardPosition()).isEqualTo(1);
        verify(history, never()).save(any());
    }
}
