package com.jobpilot.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobpilot.ai.LlmGateway;
import com.jobpilot.ai.MatchEvaluation;
import com.jobpilot.ai.ProfileSnapshot;
import com.jobpilot.ai.RequirementSnapshot;
import com.jobpilot.analysis.domain.MatchAnalysis;
import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.analysis.domain.ScoreResult;
import com.jobpilot.offer.domain.ExtractionStatus;
import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.domain.OfferRequirement;
import com.jobpilot.offer.domain.RequirementCategory;
import com.jobpilot.offer.domain.RequirementType;
import com.jobpilot.offer.domain.WorkMode;
import com.jobpilot.offer.repository.JobOfferRepository;
import com.jobpilot.offer.repository.OfferRequirementRepository;
import com.jobpilot.profile.domain.EducationItem;
import com.jobpilot.profile.domain.Experience;
import com.jobpilot.profile.domain.LanguageItem;
import com.jobpilot.profile.domain.Profile;
import com.jobpilot.profile.domain.ProfileSkill;
import com.jobpilot.profile.domain.SkillLevel;
import com.jobpilot.profile.repository.ExperienceRepository;
import com.jobpilot.profile.repository.ProfileRepository;
import com.jobpilot.profile.repository.ProfileSkillRepository;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.LlmInvalidResponseException;
import com.jobpilot.shared.error.LlmOverloadedException;
import com.jobpilot.shared.error.LlmTimeoutException;
import com.jobpilot.shared.error.NotFoundException;
import com.jobpilot.shared.error.RateLimitExceededException;
import com.jobpilot.shared.ratelimit.RateLimits;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Orquestación del análisis: 404 → 409 → cupo de IA → LLM (fuera de transacción) → puntuación en Java → una
 * transacción corta para guardarlo. Si algo falla antes de guardar, no se guarda nada.
 */
class MatchAnalysisServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final String RAW_TEXT = "Oferta de prueba. ".repeat(12);

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final OfferRequirementRepository requirements = mock(OfferRequirementRepository.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final ProfileSkillRepository skills = mock(ProfileSkillRepository.class);
    private final ExperienceRepository experiences = mock(ExperienceRepository.class);
    private final AnalysisPersistence persistence = mock(AnalysisPersistence.class);
    private final LlmGateway llm = mock(LlmGateway.class);
    private final RateLimits rateLimits = mock(RateLimits.class);

    private final MatchAnalysisService service = new MatchAnalysisService(offers, requirements, profiles, skills,
            experiences, persistence, llm, rateLimits);

    private JobOffer offer;
    private Profile profile;
    private List<OfferRequirement> offerRequirements;

    private static JobOffer offer(ExtractionStatus status) {
        return new JobOffer(USER, "Java Junior", null, "Valencia", WorkMode.HIBRIDO, null, null, null, null, RAW_TEXT,
                status, NOW);
    }

    private static List<OfferRequirement> example1Requirements(JobOffer offer) {
        List<OfferRequirement> list = new ArrayList<>();
        short order = 0;
        for (String description : List.of("Java", "SQL", "Git")) {
            list.add(new OfferRequirement(offer, description, RequirementType.OBLIGATORIO,
                    RequirementCategory.TECNICO, order++));
        }
        for (String description : List.of("React", "Docker", "Inglés B2")) {
            list.add(new OfferRequirement(offer, description, RequirementType.DESEABLE, RequirementCategory.TECNICO,
                    order++));
        }
        return list;
    }

    /** Los resultados del Ejemplo 2 (→ 85), en el orden de los requisitos. */
    private MatchEvaluation evaluation(MatchResult... results) {
        List<MatchEvaluation.Match> matches = new ArrayList<>();
        for (int i = 0; i < results.length; i++) {
            matches.add(new MatchEvaluation.Match(offerRequirements.get(i).getId(), results[i], "Evidencia " + i));
        }
        return new MatchEvaluation(matches, "Resumen", List.of("Uno", "Dos", "Tres"));
    }

    private MatchEvaluation example2() {
        return evaluation(MatchResult.CUMPLE, MatchResult.CUMPLE, MatchResult.CUMPLE, MatchResult.PARCIAL,
                MatchResult.NO_CUMPLE, MatchResult.CUMPLE);
    }

    @BeforeEach
    void readyToAnalyze() {
        offer = offer(ExtractionStatus.COMPLETADA);
        offerRequirements = example1Requirements(offer);
        profile = new Profile(USER, "Desarrolladora Java", "Backend con Spring", (short) 2, "Valencia",
                List.of(new LanguageItem("Inglés", "B2")), List.of(new EducationItem("DAW", "IES Serpis", 2023)),
                "cv.pdf", "TEXTO EN BRUTO DEL CV", NOW);

        when(offers.findByIdAndUserId(offer.getId(), USER)).thenReturn(Optional.of(offer));
        when(requirements.findByOfferIdOrdered(offer.getId())).thenReturn(offerRequirements);
        when(profiles.findByUserId(USER)).thenReturn(Optional.of(profile));
        when(skills.findByProfileIdOrdered(profile.getId()))
                .thenReturn(List.of(new ProfileSkill(profile, "Java", SkillLevel.AVANZADO, (short) 2)));
        when(experiences.findByProfileIdOrdered(profile.getId())).thenReturn(List.of(new Experience(profile,
                "Tecnologías Turia", "Desarrolladora", LocalDate.of(2024, 3, 1), null, "APIs REST", (short) 0)));
        when(llm.modelName()).thenReturn("claude-test");
    }

    private void verifyNothingCalledAfterTheChecks() {
        verifyNoInteractions(rateLimits, llm, persistence);
    }

    private static void assertConflict(Runnable call, String slug) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getTypeSlug()).isEqualTo(slug));
    }

    // ---- Camino feliz ----

    @Test
    void gastaCupoLlamaAlLlmCalculaLaPuntuacionYGuardaEnEseOrden() {
        MatchEvaluation evaluation = example2();
        when(llm.evaluateMatch(any(), anyList())).thenReturn(evaluation);
        AnalysisDetails saved = new AnalysisDetails(new MatchAnalysis(offer.getId(), profile.getId(), (short) 85,
                false, "Resumen", List.of("Uno", "Dos", "Tres"), "claude-test", NOW), List.of());
        when(persistence.save(any(), any(), anyList(), any(), any(), anyString())).thenReturn(saved);

        assertThat(service.analyze(USER, offer.getId())).isSameAs(saved);

        InOrder order = inOrder(rateLimits, llm, persistence);
        order.verify(rateLimits).consumeAiCall(USER);
        order.verify(llm).evaluateMatch(any(), anyList());
        order.verify(persistence).save(eq(offer.getId()), eq(profile.getId()), anyList(), eq(evaluation),
                eq(new ScoreResult(85, false)), eq("claude-test"));
    }

    @Test
    void alLlmLeLlegaElPerfilEstructuradoYLosRequisitosEnOrden() {
        when(llm.evaluateMatch(any(), anyList())).thenReturn(example2());

        service.analyze(USER, offer.getId());

        ArgumentCaptor<ProfileSnapshot> profileSent = ArgumentCaptor.captor();
        ArgumentCaptor<List<RequirementSnapshot>> requirementsSent = ArgumentCaptor.captor();
        verify(llm).evaluateMatch(profileSent.capture(), requirementsSent.capture());

        ProfileSnapshot sent = profileSent.getValue();
        assertThat(sent.headline()).isEqualTo("Desarrolladora Java");
        assertThat(sent.summary()).isEqualTo("Backend con Spring");
        assertThat(sent.yearsExperience()).isEqualTo((short) 2);
        assertThat(sent.location()).isEqualTo("Valencia");
        assertThat(sent.skills()).containsExactly(new ProfileSnapshot.Skill("Java", SkillLevel.AVANZADO, (short) 2));
        assertThat(sent.experiences()).containsExactly(new ProfileSnapshot.ExperienceItem("Tecnologías Turia",
                "Desarrolladora", LocalDate.of(2024, 3, 1), null, "APIs REST"));
        assertThat(sent.education()).containsExactly(new EducationItem("DAW", "IES Serpis", 2023));
        assertThat(sent.languages()).containsExactly(new LanguageItem("Inglés", "B2"));
        assertThat(sent.toString()).as("el texto en bruto del CV no se envía").doesNotContain("TEXTO EN BRUTO");

        assertThat(requirementsSent.getValue()).extracting(RequirementSnapshot::id)
                .containsExactlyElementsOf(offerRequirements.stream().map(OfferRequirement::getId).toList());
        assertThat(requirementsSent.getValue().getFirst()).isEqualTo(new RequirementSnapshot(
                offerRequirements.getFirst().getId(), "Java", RequirementType.OBLIGATORIO, RequirementCategory.TECNICO));
    }

    @Test
    void unObligatorioSinCumplirSeGuardaComoBloqueante() {
        when(llm.evaluateMatch(any(), anyList())).thenReturn(evaluation(MatchResult.NO_CUMPLE, MatchResult.CUMPLE,
                MatchResult.CUMPLE, MatchResult.PARCIAL, MatchResult.NO_CUMPLE, MatchResult.CUMPLE));

        service.analyze(USER, offer.getId());

        // obligatorios 2/3, deseables 0,5 → 46,67 + 15 = 61,67 → 62
        verify(persistence).save(any(), any(), anyList(), any(), eq(new ScoreResult(62, true)), anyString());
    }

    // ---- 404 / 409: no gastan cupo ni llaman al LLM ----

    @Test
    void unaOfertaQueNoEsDelUsuarioEs404() {
        UUID other = UUID.randomUUID();
        when(offers.findByIdAndUserId(other, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(USER, other)).isInstanceOf(NotFoundException.class);
        verifyNothingCalledAfterTheChecks();
    }

    @Test
    void sinPerfilEs409ProfileRequiredYNoSeLlamaAlLlm() {
        when(profiles.findByUserId(USER)).thenReturn(Optional.empty());

        assertConflict(() -> service.analyze(USER, offer.getId()), "profile-required");
        verifyNothingCalledAfterTheChecks();
    }

    @ParameterizedTest
    @EnumSource(value = ExtractionStatus.class, names = {"PENDIENTE", "FALLIDA"})
    void unaOfertaSinExtraerEs409(ExtractionStatus status) {
        JobOffer notExtracted = offer(status);
        when(offers.findByIdAndUserId(notExtracted.getId(), USER)).thenReturn(Optional.of(notExtracted));

        assertConflict(() -> service.analyze(USER, notExtracted.getId()), "offer-not-extracted");
        verifyNothingCalledAfterTheChecks();
    }

    @Test
    void unaOfertaCompletadaSinRequisitosEs409() {
        when(requirements.findByOfferIdOrdered(offer.getId())).thenReturn(List.of());

        assertConflict(() -> service.analyze(USER, offer.getId()), "offer-not-extracted");
        verifyNothingCalledAfterTheChecks();
    }

    @Test
    void sinPerfilYConLaOfertaSinExtraerSeAvisaPrimeroDelPerfil() {
        JobOffer failed = offer(ExtractionStatus.FALLIDA);
        when(offers.findByIdAndUserId(failed.getId(), USER)).thenReturn(Optional.of(failed));
        when(profiles.findByUserId(USER)).thenReturn(Optional.empty());

        assertConflict(() -> service.analyze(USER, failed.getId()), "profile-required");
    }

    // ---- Fallos de cupo o de IA: no se guarda nada ----

    @Test
    void sinCupoDeIaNoSeLlamaAlLlm() {
        doThrow(new RateLimitExceededException(120, "límite")).when(rateLimits).consumeAiCall(USER);

        assertThatThrownBy(() -> service.analyze(USER, offer.getId()))
                .isInstanceOf(RateLimitExceededException.class);
        verifyNoInteractions(llm, persistence);
    }

    static Stream<RuntimeException> llmErrors() {
        return Stream.of(new LlmTimeoutException("timeout"), new LlmOverloadedException(10),
                new LlmInvalidResponseException("no válida tras el reintento"));
    }

    @ParameterizedTest
    @MethodSource("llmErrors")
    void siElLlmFallaSePropagaElErrorYNoSeGuardaNada(RuntimeException error) {
        when(llm.evaluateMatch(any(), anyList())).thenThrow(error);

        assertThatThrownBy(() -> service.analyze(USER, offer.getId())).isSameAs(error);
        verifyNoInteractions(persistence);
    }

    // ---- Último análisis ----

    @Test
    void elUltimoAnalisisDeUnaOfertaPropia() {
        AnalysisDetails latest = new AnalysisDetails(new MatchAnalysis(offer.getId(), profile.getId(), (short) 70,
                false, "Resumen", List.of("Uno", "Dos", "Tres"), "fake", NOW), List.of());
        when(persistence.latest(offer.getId())).thenReturn(Optional.of(latest));

        assertThat(service.latest(USER, offer.getId())).isSameAs(latest);
    }

    @Test
    void elUltimoAnalisisDeUnaOfertaAjenaEs404SinMirarLosAnalisis() {
        UUID other = UUID.randomUUID();
        when(offers.findByIdAndUserId(other, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.latest(USER, other)).isInstanceOf(NotFoundException.class);
        verify(persistence, never()).latest(any());
    }

    @Test
    void unaOfertaSinAnalisisEs404() {
        when(persistence.latest(offer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.latest(USER, offer.getId())).isInstanceOf(NotFoundException.class);
    }
}
