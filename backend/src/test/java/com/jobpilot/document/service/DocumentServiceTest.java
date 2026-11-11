package com.jobpilot.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobpilot.ai.DocumentContext;
import com.jobpilot.ai.GeneratedDocumentContent;
import com.jobpilot.ai.LlmGateway;
import com.jobpilot.ai.ProfileSnapshot;
import com.jobpilot.analysis.domain.MatchAnalysis;
import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.analysis.repository.MatchAnalysisRepository;
import com.jobpilot.analysis.repository.RequirementMatchRepository;
import com.jobpilot.analysis.repository.RequirementMatchView;
import com.jobpilot.auth.domain.User;
import com.jobpilot.auth.repository.UserRepository;
import com.jobpilot.document.domain.DocumentType;
import com.jobpilot.document.domain.GeneratedDocument;
import com.jobpilot.document.repository.DocumentVersionView;
import com.jobpilot.document.repository.GeneratedDocumentRepository;
import com.jobpilot.offer.domain.ExtractionStatus;
import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.domain.RequirementType;
import com.jobpilot.offer.domain.WorkMode;
import com.jobpilot.offer.repository.JobOfferRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Orquestación de los documentos (JP-BE-07): 404 → 409 sin análisis → cupo de IA → LLM (fuera de transacción) →
 * firma de la carta → una transacción corta para guardar la versión. La IA no recibe el nombre del usuario: el
 * servidor lo añade como firma de la carta antes de guardarla.
 */
class DocumentServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final String RAW_TEXT = "Oferta de prueba. ".repeat(12);
    private static final String FULL_NAME = "Lucía Ferrándiz Núñez";
    private static final String EMAIL = "lucia@example.test";
    private static final String LETTER = "Estimado equipo:\n\nMe interesa el puesto.\n\nAtentamente,";

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final MatchAnalysisRepository analyses = mock(MatchAnalysisRepository.class);
    private final RequirementMatchRepository matches = mock(RequirementMatchRepository.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final ProfileSkillRepository skills = mock(ProfileSkillRepository.class);
    private final ExperienceRepository experiences = mock(ExperienceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GeneratedDocumentRepository documents = mock(GeneratedDocumentRepository.class);
    private final DocumentPersistence persistence = mock(DocumentPersistence.class);
    private final LlmGateway llm = mock(LlmGateway.class);
    private final RateLimits rateLimits = mock(RateLimits.class);
    private final PdfRenderer pdfRenderer = mock(PdfRenderer.class);

    private final DocumentService service = new DocumentService(offers, analyses, matches, profiles, skills,
            experiences, users, documents, persistence, llm, rateLimits, pdfRenderer);

    private JobOffer offer;
    private Profile profile;
    private MatchAnalysis analysis;

    @BeforeEach
    void readyToGenerate() {
        offer = new JobOffer(USER, "Desarrollador/a Java Junior", "Acme", "Valencia", WorkMode.HIBRIDO, null, null,
                null, null, RAW_TEXT, ExtractionStatus.COMPLETADA, NOW);
        profile = new Profile(USER, "Desarrolladora Java", "Backend con Spring", (short) 2, "Valencia",
                List.of(new LanguageItem("Inglés", "B2")), List.of(new EducationItem("DAW", "IES Serpis", 2023)),
                "cv.pdf", "TEXTO EN BRUTO DEL CV", NOW);
        analysis = new MatchAnalysis(offer.getId(), profile.getId(), (short) 85, false, "Cumples lo obligatorio.",
                List.of("Uno", "Dos", "Tres"), "fake", NOW);

        when(offers.findByIdAndUserId(offer.getId(), USER)).thenReturn(Optional.of(offer));
        when(analyses.findFirstByOfferIdOrderByCreatedAtDescIdDesc(offer.getId())).thenReturn(Optional.of(analysis));
        when(matches.findViewsByAnalysisId(analysis.getId())).thenReturn(List.of(
                new RequirementMatchView(UUID.randomUUID(), "Java y Spring Boot", RequirementType.OBLIGATORIO,
                        MatchResult.CUMPLE, "APIs REST"),
                new RequirementMatchView(UUID.randomUUID(), "Docker", RequirementType.DESEABLE,
                        MatchResult.NO_CUMPLE, null)));
        when(profiles.findByUserId(USER)).thenReturn(Optional.of(profile));
        when(skills.findByProfileIdOrdered(profile.getId()))
                .thenReturn(List.of(new ProfileSkill(profile, "Java", SkillLevel.AVANZADO, (short) 2)));
        when(experiences.findByProfileIdOrdered(profile.getId())).thenReturn(List.of(new Experience(profile,
                "Tecnologías Turia", "Desarrolladora", LocalDate.of(2024, 3, 1), null, "APIs REST", (short) 0)));
        when(users.findById(USER)).thenReturn(Optional.of(new User(EMAIL, "hash", FULL_NAME, NOW)));
        when(persistence.saveGenerated(any(), any(), anyString())).thenAnswer(invocation -> new GeneratedDocument(
                invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), (short) 1, false,
                NOW));
    }

    private void llmReturns(String markdown) {
        when(llm.generateDocument(any())).thenReturn(new GeneratedDocumentContent(markdown));
    }

    private DocumentContext sentContext() {
        ArgumentCaptor<DocumentContext> context = ArgumentCaptor.captor();
        verify(llm).generateDocument(context.capture());
        return context.getValue();
    }

    private static void assertConflict(Runnable call, String slug) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getTypeSlug()).isEqualTo(slug));
    }

    private GeneratedDocument document(DocumentType type, int version) {
        return new GeneratedDocument(offer.getId(), type, "# Documento", (short) version, false, NOW);
    }

    // ---- Generación: camino feliz ----

    @Test
    void gastaCupoLlamaAlLlmYGuardaLaCartaFirmadaEnEseOrden() {
        llmReturns(LETTER);

        GeneratedDocument saved = service.generate(USER, offer.getId(), DocumentType.CARTA);

        InOrder order = inOrder(rateLimits, llm, persistence);
        order.verify(rateLimits).consumeAiCall(USER);
        order.verify(llm).generateDocument(any());
        order.verify(persistence).saveGenerated(offer.getId(), DocumentType.CARTA, LETTER + "\n\n" + FULL_NAME);
        assertThat(saved.getContentMarkdown()).isEqualTo(LETTER + "\n\n" + FULL_NAME);
    }

    @Test
    void laFirmaVaDebajoDeLaDespedidaSinEspaciosSobrantes() {
        llmReturns(LETTER + "  \n\n\n");
        when(users.findById(USER)).thenReturn(Optional.of(new User(EMAIL, "hash", "  " + FULL_NAME + " ", NOW)));

        service.generate(USER, offer.getId(), DocumentType.CARTA);

        verify(persistence).saveGenerated(offer.getId(), DocumentType.CARTA, LETTER + "\n\n" + FULL_NAME);
    }

    @Test
    void losCaracteresDeMarkdownDelNombreSeEscapanEnLaFirma() {
        llmReturns(LETTER);
        when(users.findById(USER)).thenReturn(Optional.of(
                new User(EMAIL, "hash", "Ana *Pérez* _G_ [x](http://evil.test) #1 `c` \\o/", NOW)));

        service.generate(USER, offer.getId(), DocumentType.CARTA);

        verify(persistence).saveGenerated(offer.getId(), DocumentType.CARTA, LETTER + "\n\n"
                + "Ana \\*Pérez\\* \\_G\\_ \\[x\\]\\(http://evil.test\\) \\#1 \\`c\\` \\\\o/");
    }

    @Test
    void escaparLaFirmaNoCambiaUnNombreNormal() {
        assertThat(DocumentService.escapeMarkdown("Lucía Ferrándiz-Núñez O'Brien")).isEqualTo(
                "Lucía Ferrándiz-Núñez O'Brien");
    }

    @Test
    void elCvAdaptadoSeGuardaTalCualSinFirma() {
        llmReturns("# Desarrolladora Java\n\n- Java");

        service.generate(USER, offer.getId(), DocumentType.CV_ADAPTADO);

        verify(persistence).saveGenerated(offer.getId(), DocumentType.CV_ADAPTADO, "# Desarrolladora Java\n\n- Java");
        verifyNoInteractions(users);
    }

    @Test
    void alLlmLeLleganLaOfertaElPerfilYElUltimoAnalisisPeroNiElNombreNiElEmail() {
        llmReturns(LETTER);

        service.generate(USER, offer.getId(), DocumentType.CARTA);

        DocumentContext sent = sentContext();
        assertThat(sent.type()).isEqualTo(DocumentType.CARTA);
        assertThat(sent.offer()).isEqualTo(new DocumentContext.OfferData("Desarrollador/a Java Junior", "Acme",
                "Valencia", WorkMode.HIBRIDO, RAW_TEXT));
        assertThat(sent.profile()).isEqualTo(new ProfileSnapshot("Desarrolladora Java", "Backend con Spring",
                (short) 2, "Valencia", List.of(new ProfileSnapshot.Skill("Java", SkillLevel.AVANZADO, (short) 2)),
                List.of(new ProfileSnapshot.ExperienceItem("Tecnologías Turia", "Desarrolladora",
                        LocalDate.of(2024, 3, 1), null, "APIs REST")),
                List.of(new EducationItem("DAW", "IES Serpis", 2023)), List.of(new LanguageItem("Inglés", "B2"))));
        assertThat(sent.matchSummary()).isEqualTo("Cumples lo obligatorio.");
        assertThat(sent.requirements()).containsExactly(
                new DocumentContext.RequirementResult("Java y Spring Boot", RequirementType.OBLIGATORIO,
                        MatchResult.CUMPLE, "APIs REST"),
                new DocumentContext.RequirementResult("Docker", RequirementType.DESEABLE, MatchResult.NO_CUMPLE,
                        null));
        assertThat(sent.toString()).doesNotContain(FULL_NAME).doesNotContain("Lucía").doesNotContain(EMAIL)
                .doesNotContain("TEXTO EN BRUTO");
    }

    // ---- Generación: versión ya ocupada → se recalcula una vez ----

    @Test
    void siOtraEscrituraOcupaLaVersionSeReintentaUnaVezSinVolverALlamarAlLlm() {
        llmReturns(LETTER);
        GeneratedDocument second = document(DocumentType.CARTA, 2);
        when(persistence.saveGenerated(any(), any(), anyString()))
                .thenThrow(DocumentPersistence.versionConflict())
                .thenReturn(second);

        assertThat(service.generate(USER, offer.getId(), DocumentType.CARTA)).isSameAs(second);
        verify(persistence, times(2)).saveGenerated(eq(offer.getId()), eq(DocumentType.CARTA), anyString());
        verify(llm, times(1)).generateDocument(any());
    }

    @Test
    void siVuelveAChocarEs409() {
        llmReturns(LETTER);
        when(persistence.saveGenerated(any(), any(), anyString())).thenThrow(DocumentPersistence.versionConflict());

        assertConflict(() -> service.generate(USER, offer.getId(), DocumentType.CARTA), "document-version-conflict");
        verify(persistence, times(2)).saveGenerated(any(), any(), anyString());
    }

    // ---- Generación: 404 / 409 no gastan cupo ni llaman al LLM ----

    @Test
    void unaOfertaQueNoEsDelUsuarioEs404() {
        UUID other = UUID.randomUUID();
        when(offers.findByIdAndUserId(other, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(USER, other, DocumentType.CARTA))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(rateLimits, llm, persistence);
    }

    @Test
    void sinAnalisisEs409AnalysisRequired() {
        when(analyses.findFirstByOfferIdOrderByCreatedAtDescIdDesc(offer.getId())).thenReturn(Optional.empty());

        assertConflict(() -> service.generate(USER, offer.getId(), DocumentType.CARTA), "analysis-required");
        verifyNoInteractions(rateLimits, llm, persistence);
    }

    @Test
    void sinPerfilEs409ProfileRequired() {
        when(profiles.findByUserId(USER)).thenReturn(Optional.empty());

        assertConflict(() -> service.generate(USER, offer.getId(), DocumentType.CV_ADAPTADO), "profile-required");
        verifyNoInteractions(rateLimits, llm, persistence);
    }

    // ---- Generación: fallos de cupo o de IA, sin guardar nada ----

    @Test
    void sinCupoDeIaNoSeLlamaAlLlm() {
        doThrow(new RateLimitExceededException(120, "límite")).when(rateLimits).consumeAiCall(USER);

        assertThatThrownBy(() -> service.generate(USER, offer.getId(), DocumentType.CARTA))
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
        when(llm.generateDocument(any())).thenThrow(error);

        assertThatThrownBy(() -> service.generate(USER, offer.getId(), DocumentType.CARTA)).isSameAs(error);
        verifyNoInteractions(persistence);
    }

    // ---- Historial ----

    @Test
    void elHistorialDeUnaOfertaPropia() {
        DocumentHistory history = new DocumentHistory(document(DocumentType.CARTA, 2), List.of(
                new DocumentVersionView(UUID.randomUUID(), (short) 2, true, NOW),
                new DocumentVersionView(UUID.randomUUID(), (short) 1, false, NOW)));
        when(persistence.history(offer.getId(), DocumentType.CARTA)).thenReturn(Optional.of(history));

        assertThat(service.history(USER, offer.getId(), DocumentType.CARTA)).isSameAs(history);
    }

    @Test
    void unaOfertaSinDocumentosDeEseTipoEs404() {
        when(persistence.history(offer.getId(), DocumentType.CV_ADAPTADO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(USER, offer.getId(), DocumentType.CV_ADAPTADO))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void elHistorialDeUnaOfertaAjenaEs404SinMirarLosDocumentos() {
        UUID other = UUID.randomUUID();
        when(offers.findByIdAndUserId(other, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(USER, other, DocumentType.CARTA))
                .isInstanceOf(NotFoundException.class);
        verify(persistence, never()).history(any(), any());
    }

    // ---- Edición ----

    @Test
    void editarGuardaUnaVersionNuevaAPartirDeLaDelDocumentoSinIa() {
        GeneratedDocument base = document(DocumentType.CARTA, 1);
        GeneratedDocument edited = new GeneratedDocument(offer.getId(), DocumentType.CARTA, "Nuevo", (short) 2, true,
                NOW);
        when(documents.findByIdAndUserId(base.getId(), USER)).thenReturn(Optional.of(base));
        when(persistence.saveEdit(base, "Nuevo")).thenReturn(edited);

        assertThat(service.createVersion(USER, base.getId(), "Nuevo")).isSameAs(edited);
        verifyNoInteractions(rateLimits, llm);
    }

    @Test
    void editarUnDocumentoAjenoOInexistenteEs404() {
        UUID other = UUID.randomUUID();
        when(documents.findByIdAndUserId(other, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createVersion(USER, other, "Nuevo")).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(persistence);
    }

    // ---- PDF ----

    @Test
    void elPdfLlevaElNombreDelUsuarioYElContenidoDeEsaVersion() {
        GeneratedDocument letter = document(DocumentType.CARTA, 3);
        when(documents.findByIdAndUserId(letter.getId(), USER)).thenReturn(Optional.of(letter));
        byte[] bytes = {'%', 'P', 'D', 'F'};
        when(pdfRenderer.render(FULL_NAME, "# Documento")).thenReturn(bytes);

        DocumentPdf pdf = service.pdf(USER, letter.getId());

        assertThat(pdf.content()).isSameAs(bytes);
        assertThat(pdf.fileName()).isEqualTo("carta-v3.pdf");
    }

    @Test
    void elPdfDelCvAdaptadoSeLlamaCvAdaptado() {
        GeneratedDocument cv = document(DocumentType.CV_ADAPTADO, 1);
        when(documents.findByIdAndUserId(cv.getId(), USER)).thenReturn(Optional.of(cv));
        when(pdfRenderer.render(anyString(), anyString())).thenReturn(new byte[0]);

        assertThat(service.pdf(USER, cv.getId()).fileName()).isEqualTo("cv-adaptado-v1.pdf");
    }

    @Test
    void elPdfDeUnDocumentoAjenoEs404() {
        UUID other = UUID.randomUUID();
        when(documents.findByIdAndUserId(other, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pdf(USER, other)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(pdfRenderer);
    }
}
