package com.jobpilot.tracker.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ApplicationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    private static Application saved(LocalDate appliedAt) {
        return new Application(UUID.randomUUID(), UUID.randomUUID(), ApplicationStatus.GUARDADA, 0, appliedAt, null,
                null, NOW.minusSeconds(3600));
    }

    @Test
    void movingToEnviadaSetsAppliedAtToTodayWhenEmpty() {
        Application application = saved(null);

        ApplicationStatusHistory history = application.changeStatus(ApplicationStatus.ENVIADA, TODAY, NOW);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ENVIADA);
        assertThat(application.getAppliedAt()).isEqualTo(TODAY);
        assertThat(application.getUpdatedAt()).isEqualTo(NOW);
        assertThat(history.getApplication()).isSameAs(application);
        assertThat(history.getFromStatus()).isEqualTo(ApplicationStatus.GUARDADA);
        assertThat(history.getToStatus()).isEqualTo(ApplicationStatus.ENVIADA);
        assertThat(history.getChangedAt()).isEqualTo(NOW);
    }

    @Test
    void movingToEnviadaKeepsAnExistingAppliedAt() {
        LocalDate earlier = TODAY.minusDays(20);
        Application application = saved(earlier);

        application.changeStatus(ApplicationStatus.ENVIADA, TODAY, NOW);

        assertThat(application.getAppliedAt()).isEqualTo(earlier);
    }

    @Test
    void discardingDoesNotTouchAppliedAt() {
        Application application = saved(null);

        application.changeStatus(ApplicationStatus.DESCARTADA, TODAY, NOW);

        assertThat(application.getAppliedAt()).isNull();
    }

    @Test
    void creationHistoryHasNoOrigin() {
        Application application = saved(null);

        ApplicationStatusHistory history = application.creationHistory();

        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(ApplicationStatus.GUARDADA);
        assertThat(history.getChangedAt()).isEqualTo(application.getCreatedAt());
    }

    @Test
    void followUpBeforeTodayIsOverdue() {
        assertThat(Application.isFollowUpOverdue(ApplicationStatus.ENVIADA, TODAY.minusDays(1), TODAY)).isTrue();
    }

    @Test
    void followUpTodayOrLaterIsNotOverdue() {
        assertThat(Application.isFollowUpOverdue(ApplicationStatus.ENVIADA, TODAY, TODAY)).isFalse();
        assertThat(Application.isFollowUpOverdue(ApplicationStatus.ENVIADA, TODAY.plusDays(1), TODAY)).isFalse();
    }

    @Test
    void withoutFollowUpDateNothingIsOverdue() {
        assertThat(Application.isFollowUpOverdue(ApplicationStatus.ENVIADA, null, TODAY)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = {"OFERTA", "DESCARTADA"})
    void closedApplicationsAreNeverOverdue(ApplicationStatus status) {
        assertThat(Application.isFollowUpOverdue(status, TODAY.minusDays(30), TODAY)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = {"GUARDADA", "ENVIADA", "ENTREVISTA"})
    void openApplicationsCanBeOverdue(ApplicationStatus status) {
        assertThat(Application.isFollowUpOverdue(status, TODAY.minusDays(30), TODAY)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = {"ENVIADA", "ENTREVISTA", "OFERTA"})
    void statusesThatRequireAppliedAt(ApplicationStatus status) {
        assertThat(status.requiresAppliedAt()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = {"GUARDADA", "DESCARTADA"})
    void statusesThatDoNotRequireAppliedAt(ApplicationStatus status) {
        assertThat(status.requiresAppliedAt()).isFalse();
    }
}
