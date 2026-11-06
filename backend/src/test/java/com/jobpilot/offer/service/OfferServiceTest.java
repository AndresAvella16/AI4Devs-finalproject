package com.jobpilot.offer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobpilot.ai.LlmGateway;
import com.jobpilot.ai.OfferExtraction;
import com.jobpilot.offer.domain.ExtractionStatus;
import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.domain.RequirementCategory;
import com.jobpilot.offer.domain.RequirementType;
import com.jobpilot.offer.domain.WorkMode;
import com.jobpilot.offer.repository.JobOfferRepository;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.LlmInvalidResponseException;
import com.jobpilot.shared.error.LlmOverloadedException;
import com.jobpilot.shared.error.LlmTimeoutException;
import com.jobpilot.shared.error.NotFoundException;
import com.jobpilot.shared.error.RateLimitExceededException;
import com.jobpilot.shared.ratelimit.RateLimits;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

/** Orquestación de la importación: PENDIENTE → LLM fuera de transacción → COMPLETADA o FALLIDA. */
class OfferServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final String RAW_TEXT = "Oferta de prueba. ".repeat(12);
    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    private static final OfferExtraction EXTRACTION = new OfferExtraction("Java Junior", "ACME", "Valencia",
            WorkMode.HIBRIDO, null,
            List.of(new OfferExtraction.Requirement("Java", RequirementType.OBLIGATORIO, RequirementCategory.TECNICO)));

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final OfferPersistence persistence = mock(OfferPersistence.class);
    private final LlmGateway llm = mock(LlmGateway.class);
    private final RateLimits rateLimits = mock(RateLimits.class);

    private final OfferService service = new OfferService(offers, persistence, llm, rateLimits);

    private static JobOffer offer(ExtractionStatus status) {
        JobOffer offer = JobOffer.pending(USER, RAW_TEXT, null, NOW);
        if (status == ExtractionStatus.FALLIDA) {
            offer.failExtraction();
        } else if (status == ExtractionStatus.COMPLETADA) {
            offer.completeExtraction("Java", null, null, WorkMode.REMOTO, null, null, null);
        }
        return offer;
    }

    private static OfferDetails details(JobOffer offer) {
        return new OfferDetails(offer, List.of());
    }

    static Stream<RuntimeException> llmErrors() {
        return Stream.of(new LlmTimeoutException("timeout"), new LlmOverloadedException(10),
                new LlmInvalidResponseException("no válida"));
    }

    // ---- POST /offers ----

    @Test
    void creaLaOfertaEnPendienteLlamaAlLlmYGuardaLaExtraccion() {
        JobOffer pending = offer(ExtractionStatus.PENDIENTE);
        when(persistence.createPending(USER, RAW_TEXT, "https://acme.dev/oferta")).thenReturn(pending);
        when(llm.extractOffer(RAW_TEXT)).thenReturn(EXTRACTION);
        OfferDetails completed = details(offer(ExtractionStatus.COMPLETADA));
        when(persistence.complete(pending.getId(), EXTRACTION)).thenReturn(completed);

        assertThat(service.create(USER, RAW_TEXT, "https://acme.dev/oferta")).isSameAs(completed);

        InOrder order = inOrder(rateLimits, persistence, llm);
        order.verify(rateLimits).consumeAiCall(USER);
        order.verify(persistence).createPending(USER, RAW_TEXT, "https://acme.dev/oferta");
        order.verify(llm).extractOffer(RAW_TEXT);
        order.verify(persistence).complete(pending.getId(), EXTRACTION);
    }

    @Test
    void unaUrlEnBlancoSeGuardaComoNull() {
        JobOffer pending = offer(ExtractionStatus.PENDIENTE);
        when(persistence.createPending(USER, RAW_TEXT, null)).thenReturn(pending);
        when(llm.extractOffer(RAW_TEXT)).thenReturn(EXTRACTION);

        service.create(USER, RAW_TEXT, "   ");

        verify(persistence).createPending(USER, RAW_TEXT, null);
    }

    @ParameterizedTest
    @MethodSource("llmErrors")
    void siElLlmFallaLaOfertaQuedaFallidaYNoSeLanzaError(RuntimeException error) {
        JobOffer pending = offer(ExtractionStatus.PENDIENTE);
        when(persistence.createPending(USER, RAW_TEXT, null)).thenReturn(pending);
        when(llm.extractOffer(RAW_TEXT)).thenThrow(error);
        OfferDetails failed = details(offer(ExtractionStatus.FALLIDA));
        when(persistence.markFailed(pending.getId())).thenReturn(failed);

        assertThat(service.create(USER, RAW_TEXT, null)).isSameAs(failed);
        verify(persistence, never()).complete(any(), any());
    }

    @Test
    void unErrorQueNoEsDeIaSePropagaYLaOfertaSigueGuardadaEnPendiente() {
        JobOffer pending = offer(ExtractionStatus.PENDIENTE);
        when(persistence.createPending(USER, RAW_TEXT, null)).thenReturn(pending);
        when(llm.extractOffer(RAW_TEXT)).thenThrow(new IllegalStateException("API key no válida"));

        assertThatThrownBy(() -> service.create(USER, RAW_TEXT, null)).isInstanceOf(IllegalStateException.class);
        verify(persistence).createPending(USER, RAW_TEXT, null);
        verify(persistence, never()).markFailed(any());
    }

    @Test
    void siSeSuperaElLimiteNoSeGuardaNadaNiSeLlamaAlLlm() {
        doThrow(new RateLimitExceededException(120, "límite")).when(rateLimits).consumeAiCall(USER);

        assertThatThrownBy(() -> service.create(USER, RAW_TEXT, null)).isInstanceOf(RateLimitExceededException.class);
        verifyNoInteractions(persistence, llm);
    }

    // ---- POST /offers/{id}/extraction ----

    @Test
    void reintentarUnaOfertaFallidaLlamaAlLlmYReemplazaLosRequisitos() {
        JobOffer failed = offer(ExtractionStatus.FALLIDA);
        when(offers.findByIdAndUserId(failed.getId(), USER)).thenReturn(Optional.of(failed));
        when(llm.extractOffer(RAW_TEXT)).thenReturn(EXTRACTION);
        OfferDetails completed = details(offer(ExtractionStatus.COMPLETADA));
        when(persistence.complete(failed.getId(), EXTRACTION)).thenReturn(completed);

        assertThat(service.retryExtraction(USER, failed.getId())).isSameAs(completed);
        verify(rateLimits).consumeAiCall(USER);
    }

    @Test
    void tambienSePuedeReintentarUnaOfertaPendiente() {
        JobOffer pending = offer(ExtractionStatus.PENDIENTE);
        when(offers.findByIdAndUserId(pending.getId(), USER)).thenReturn(Optional.of(pending));
        when(llm.extractOffer(RAW_TEXT)).thenReturn(EXTRACTION);

        service.retryExtraction(USER, pending.getId());

        verify(persistence).complete(pending.getId(), EXTRACTION);
    }

    @Test
    void reintentarUnaOfertaCompletadaEsConflictoSinGastarCupoNiLlamarAlLlm() {
        JobOffer completed = offer(ExtractionStatus.COMPLETADA);
        when(offers.findByIdAndUserId(completed.getId(), USER)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.retryExtraction(USER, completed.getId()))
                .isInstanceOf(ConflictException.class)
                .extracting("typeSlug").isEqualTo("offer-already-extracted");
        verifyNoInteractions(llm, rateLimits);
    }

    @Test
    void reintentarUnaOfertaAjenaOInexistenteEsNoEncontrada() {
        UUID offerId = UUID.randomUUID();
        when(offers.findByIdAndUserId(offerId, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryExtraction(USER, offerId)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(llm, rateLimits);
    }

    @ParameterizedTest
    @MethodSource("llmErrors")
    void siElReintentoFallaLaOfertaQuedaFallidaYSePropagaElError(RuntimeException error) {
        JobOffer pending = offer(ExtractionStatus.PENDIENTE);
        when(offers.findByIdAndUserId(pending.getId(), USER)).thenReturn(Optional.of(pending));
        when(llm.extractOffer(RAW_TEXT)).thenThrow(error);

        assertThatThrownBy(() -> service.retryExtraction(USER, pending.getId())).isSameAs(error);
        verify(persistence).markFailed(pending.getId());
        verify(persistence, never()).complete(any(), any());
    }
}
