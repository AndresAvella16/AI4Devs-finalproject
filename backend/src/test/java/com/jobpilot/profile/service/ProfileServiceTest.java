package com.jobpilot.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobpilot.ai.LlmGateway;
import com.jobpilot.ai.ProfileExtraction;
import com.jobpilot.profile.domain.EducationItem;
import com.jobpilot.profile.domain.LanguageItem;
import com.jobpilot.profile.domain.Profile;
import com.jobpilot.profile.domain.SkillLevel;
import com.jobpilot.profile.repository.ProfileRepository;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.CvWithoutTextException;
import com.jobpilot.shared.error.InvalidCvFileException;
import com.jobpilot.shared.error.InvalidRequestException;
import com.jobpilot.shared.error.LlmTimeoutException;
import com.jobpilot.shared.error.RateLimitExceededException;
import com.jobpilot.shared.ratelimit.RateLimits;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Orquestación de JP-BE-04 sin BD: qué se comprueba antes de gastar cupo y llamar a la IA, qué se guarda en el
 * borrador en memoria y cómo se traduce la carrera del primer {@code PUT /profile} a un 409.
 */
class ProfileServiceTest {

    private static final String PDF = "application/pdf";
    private static final byte[] BYTES = {'%', 'P', 'D', 'F'};
    private static final String CV_TEXT = "Texto extraído del CV";

    private final CvTextExtractor extractor = mock(CvTextExtractor.class);
    private final CvDraftStore drafts = mock(CvDraftStore.class);
    private final ProfilePersistence persistence = mock(ProfilePersistence.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final LlmGateway llm = mock(LlmGateway.class);
    private final RateLimits rateLimits = mock(RateLimits.class);

    private final ProfileService service = new ProfileService(extractor, drafts, persistence, profiles, llm,
            rateLimits);

    private final UUID userId = UUID.randomUUID();

    private static ProfileExtraction extraction(List<ProfileExtraction.Skill> skills) {
        return new ProfileExtraction("Desarrolladora Java", "Resumen", 2, "Valencia", skills,
                List.of(new ProfileExtraction.Experience("ACME", "Junior", LocalDate.of(2025, 3, 1), null,
                        "APIs REST")),
                List.of(new EducationItem("DAW", "IES", 2024)), List.of(new LanguageItem("Inglés", "B2")));
    }

    private static ProfileData data(List<ProfileData.Skill> skills, List<ProfileData.Experience> experiences) {
        return new ProfileData("Titular", null, 2, null, List.of(), List.of(), skills, experiences);
    }

    private static ProfileData.Experience experience(LocalDate start, LocalDate end) {
        return new ProfileData.Experience("ACME", "Junior", start, end, null);
    }

    // ---- POST /profile/cv ----

    @Test
    void subirUnCvValidoExtraeElTextoGastaCupoLlamaALaIaYGuardaElBorrador() {
        when(extractor.extract(PDF, BYTES)).thenReturn(CV_TEXT);
        when(llm.extractProfile(CV_TEXT)).thenReturn(extraction(List.of(
                new ProfileExtraction.Skill("Java", SkillLevel.AVANZADO, 2))));

        CvDraftResult result = service.uploadCv(userId, "mi-cv.pdf", PDF, BYTES);

        InOrder order = inOrder(extractor, rateLimits, llm, drafts);
        order.verify(extractor).extract(PDF, BYTES);
        order.verify(rateLimits).consumeAiCall(userId);
        order.verify(llm).extractProfile(CV_TEXT);
        order.verify(drafts).put(userId, "mi-cv.pdf", CV_TEXT);
        assertThat(result.cvFileName()).isEqualTo("mi-cv.pdf");
        assertThat(result.draft().headline()).isEqualTo("Desarrolladora Java");
        assertThat(result.draft().yearsExperience()).isEqualTo(2);
        assertThat(result.draft().skills()).containsExactly(new ProfileData.Skill("Java", SkillLevel.AVANZADO, 2));
        assertThat(result.draft().experiences()).containsExactly(
                new ProfileData.Experience("ACME", "Junior", LocalDate.of(2025, 3, 1), null, "APIs REST"));
        assertThat(result.draft().education()).containsExactly(new EducationItem("DAW", "IES", 2024));
        assertThat(result.draft().languages()).containsExactly(new LanguageItem("Inglés", "B2"));
        verifyNoInteractions(persistence);
    }

    @Test
    void lasHabilidadesRepetidasDelBorradorSeQuitanSinDistinguirMayusculas() {
        when(extractor.extract(PDF, BYTES)).thenReturn(CV_TEXT);
        when(llm.extractProfile(CV_TEXT)).thenReturn(extraction(List.of(
                new ProfileExtraction.Skill("Java", SkillLevel.AVANZADO, 2),
                new ProfileExtraction.Skill("Spring Boot", null, null),
                new ProfileExtraction.Skill(" JAVA ", SkillLevel.BASICO, 1))));

        CvDraftResult result = service.uploadCv(userId, "cv.pdf", PDF, BYTES);

        assertThat(result.draft().skills()).extracting(ProfileData.Skill::name).containsExactly("Java",
                "Spring Boot");
    }

    @Test
    void unFicheroNoValidoNoGastaCupoNiLlamaALaIa() {
        when(extractor.extract(PDF, BYTES)).thenThrow(new InvalidCvFileException());

        assertThatThrownBy(() -> service.uploadCv(userId, "cv.pdf", PDF, BYTES))
                .isInstanceOf(InvalidCvFileException.class);
        verifyNoInteractions(rateLimits, llm, drafts);
    }

    @Test
    void unPdfSinTextoNoGastaCupoNiLlamaALaIa() {
        when(extractor.extract(PDF, BYTES)).thenThrow(new CvWithoutTextException());

        assertThatThrownBy(() -> service.uploadCv(userId, "cv.pdf", PDF, BYTES))
                .isInstanceOf(CvWithoutTextException.class);
        verifyNoInteractions(rateLimits, llm, drafts);
    }

    @Test
    void sinCupoNoSeLlamaALaIa() {
        when(extractor.extract(PDF, BYTES)).thenReturn(CV_TEXT);
        org.mockito.Mockito.doThrow(new RateLimitExceededException(120, "límite")).when(rateLimits)
                .consumeAiCall(userId);

        assertThatThrownBy(() -> service.uploadCv(userId, "cv.pdf", PDF, BYTES))
                .isInstanceOf(RateLimitExceededException.class);
        verifyNoInteractions(llm, drafts);
    }

    @Test
    void siLaIaFallaNoSeGuardaElBorrador() {
        when(extractor.extract(PDF, BYTES)).thenReturn(CV_TEXT);
        when(llm.extractProfile(anyString())).thenThrow(new LlmTimeoutException("timeout"));

        assertThatThrownBy(() -> service.uploadCv(userId, "cv.pdf", PDF, BYTES))
                .isInstanceOf(LlmTimeoutException.class);
        verifyNoInteractions(drafts);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "mi-cv.pdf, mi-cv.pdf",
            "'  CV Laura.pdf  ', CV Laura.pdf",
            "C:\\Users\\laura\\Documents\\cv.pdf, cv.pdf",
            "../../etc/cv.pdf, cv.pdf",
            "NULL, cv.pdf",
            "'   ', cv.pdf",
            "carpeta/, cv.pdf"})
    void elNombreDelFicheroSeLimpia(String original, String expected) {
        assertThat(ProfileService.cleanFileName(original)).isEqualTo(expected);
    }

    @Test
    void elNombreDelFicheroNoPasaDe255Caracteres() {
        assertThat(ProfileService.cleanFileName("x".repeat(300) + ".pdf")).hasSize(255);
        assertThat(ProfileService.cleanFileName("cv\u0000\u0007.pdf")).isEqualTo("cv.pdf");
    }

    // ---- PUT /profile ----

    @Test
    void guardarSinBorradorGuardaElPerfilSinCv() {
        ProfileData data = data(List.of(), List.of());
        SavedProfile saved = new SavedProfile(null, true);
        when(drafts.find(userId)).thenReturn(Optional.empty());
        when(persistence.save(userId, data, null)).thenReturn(saved);

        assertThat(service.save(userId, data)).isSameAs(saved);
        verify(drafts, never()).remove(any(), any());
    }

    @Test
    void guardarConBorradorLoAnadeAlPerfilYDespuesLoOlvida() {
        ProfileData data = data(List.of(), List.of());
        CvDraft draft = new CvDraft("cv.pdf", CV_TEXT, Instant.now());
        SavedProfile saved = new SavedProfile(null, true);
        when(drafts.find(userId)).thenReturn(Optional.of(draft));
        when(persistence.save(userId, data, draft)).thenReturn(saved);

        assertThat(service.save(userId, data)).isSameAs(saved);
        InOrder order = inOrder(persistence, drafts);
        order.verify(persistence).save(userId, data, draft);
        order.verify(drafts).remove(userId, draft);
    }

    @Test
    void siFallaElGuardadoElBorradorNoSeOlvida() {
        ProfileData data = data(List.of(), List.of());
        CvDraft draft = new CvDraft("cv.pdf", CV_TEXT, Instant.now());
        when(drafts.find(userId)).thenReturn(Optional.of(draft));
        when(persistence.save(userId, data, draft)).thenThrow(new IllegalStateException("BD caída"));

        assertThatThrownBy(() -> service.save(userId, data)).isInstanceOf(IllegalStateException.class);
        verify(drafts, never()).remove(any(), any());
    }

    @Test
    void siOtraPeticionHaCreadoElPerfilALaVezEsUnConflicto() {
        ProfileData data = data(List.of(), List.of());
        when(drafts.find(userId)).thenReturn(Optional.empty());
        when(persistence.save(eq(userId), eq(data), isNull()))
                .thenThrow(new DataIntegrityViolationException("uq_profiles_user_id"));
        when(profiles.findByUserId(userId)).thenReturn(Optional.of(mock(Profile.class)));

        assertThatThrownBy(() -> service.save(userId, data))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getTypeSlug()).isEqualTo("profile-concurrent-update"));
    }

    @Test
    void unaViolacionDeIntegridadSinPerfilGuardadoNoSeConvierteEnConflicto() {
        ProfileData data = data(List.of(), List.of());
        DataIntegrityViolationException error = new DataIntegrityViolationException("otra");
        when(drafts.find(userId)).thenReturn(Optional.empty());
        when(persistence.save(eq(userId), eq(data), isNull())).thenThrow(error);
        when(profiles.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(userId, data)).isSameAs(error);
    }

    @Test
    void lasHabilidadesRepetidasSinDistinguirMayusculasSonUnErrorDeValidacion() {
        ProfileData data = data(List.of(new ProfileData.Skill("Java", null, null),
                new ProfileData.Skill("Spring", null, null), new ProfileData.Skill("java ", null, null)), List.of());

        assertThatThrownBy(() -> service.save(userId, data))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("skills[2].name"));
        verifyNoInteractions(persistence);
    }

    @Test
    void unaExperienciaQueAcabaAntesDeEmpezarEsUnErrorDeValidacion() {
        ProfileData data = data(List.of(), List.of(
                experience(LocalDate.of(2024, 1, 1), null),
                experience(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 5, 31))));

        assertThatThrownBy(() -> service.save(userId, data))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("experiences[1].endDate"));
        verifyNoInteractions(persistence);
    }

    @Test
    void unaExperienciaQueEmpiezaYAcabaElMismoDiaEsValida() {
        ProfileData data = data(List.of(), List.of(experience(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1))));
        when(drafts.find(userId)).thenReturn(Optional.empty());

        service.save(userId, data);

        verify(persistence).save(userId, data, null);
    }
}
