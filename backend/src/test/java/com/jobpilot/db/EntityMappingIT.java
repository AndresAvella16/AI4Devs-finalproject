package com.jobpilot.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.analysis.domain.MatchAnalysis;
import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.analysis.domain.RequirementMatch;
import com.jobpilot.auth.domain.User;
import com.jobpilot.document.domain.DocumentType;
import com.jobpilot.document.domain.GeneratedDocument;
import com.jobpilot.offer.domain.ExtractionStatus;
import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.domain.OfferRequirement;
import com.jobpilot.offer.domain.RequirementCategory;
import com.jobpilot.offer.domain.RequirementType;
import com.jobpilot.offer.domain.WorkMode;
import com.jobpilot.profile.domain.EducationItem;
import com.jobpilot.profile.domain.Experience;
import com.jobpilot.profile.domain.LanguageItem;
import com.jobpilot.profile.domain.Profile;
import com.jobpilot.profile.domain.ProfileSkill;
import com.jobpilot.profile.domain.SkillLevel;
import com.jobpilot.support.PostgresTestcontainer;
import com.jobpilot.support.TestRows;
import com.jobpilot.tracker.domain.Application;
import com.jobpilot.tracker.domain.ApplicationStatus;
import com.jobpilot.tracker.domain.ApplicationStatusHistory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * CA 1 a nivel JPA: Hibernate valida el esquema al arrancar (ddl-auto=validate) y cada entidad se guarda y se
 * vuelve a leer sin perder datos, incluidos los JSONB (Jackson 3) y los enumerados. Cada test se revierte.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
@Transactional
class EntityMappingIT {

    /** PostgreSQL guarda microsegundos: el instante de prueba no tiene más precisión. */
    private static final Instant NOW = Instant.parse("2026-11-06T10:15:30.123456Z");

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private <T> T reload(T entity, Class<T> type, Object id) {
        em.persist(entity);
        em.flush();
        em.clear();
        return em.find(type, id);
    }

    private User persistedUser() {
        User user = new User("ana@test.dev", "$2a$12$hash", "Ana Test", NOW);
        em.persist(user);
        return user;
    }

    private JobOffer persistedOffer(User user) {
        JobOffer offer = new JobOffer(user.getId(), "Java Junior", "Empresa", "Valencia", WorkMode.HIBRIDO,
                24000, 28000, "EUR", "https://example.com/oferta", TestRows.VALID_RAW_TEXT,
                ExtractionStatus.COMPLETADA, NOW);
        em.persist(offer);
        return offer;
    }

    private Profile persistedProfile(User user) {
        Profile profile = new Profile(user.getId(), "Dev Junior", "Resumen", (short) 2, "Valencia",
                List.of(), List.of(), null, null, NOW);
        em.persist(profile);
        return profile;
    }

    @Test
    void hibernateValidaUnaEntidadPorCadaTablaDelModelo() {
        List<String> tables = em.getMetamodel().getEntities().stream()
                .map(entity -> entity.getJavaType().getAnnotation(jakarta.persistence.Table.class).name())
                .toList();

        assertThat(tables).containsExactlyInAnyOrder(
                "users", "profiles", "profile_skills", "experiences", "job_offers", "offer_requirements",
                "match_analyses", "requirement_matches", "generated_documents", "applications",
                "application_status_history");
    }

    @Test
    void user() {
        User user = new User("ana@test.dev", "$2a$12$hash", "Ana Test", NOW);

        User found = reload(user, User.class, user.getId());

        assertThat(found.getId()).isNotNull();
        assertThat(found.getEmail()).isEqualTo("ana@test.dev");
        assertThat(found.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(found.getFullName()).isEqualTo("Ana Test");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void perfilConJsonbHabilidadesYExperiencias() {
        User user = persistedUser();
        Profile profile = new Profile(user.getId(), "Dev Junior", "Resumen", (short) 2, "Valencia",
                List.of(new LanguageItem("Inglés", "B2"), new LanguageItem("Español", "Nativo")),
                List.of(new EducationItem("DAW", "IES Test", 2024)),
                "cv.pdf", "Texto del CV", NOW);
        em.persist(profile);
        ProfileSkill skill = new ProfileSkill(profile, "Spring Boot", SkillLevel.INTERMEDIO, (short) 2);
        Experience experience = new Experience(profile, "Empresa", "Dev", LocalDate.of(2024, 3, 1), null,
                "Descripción", (short) 0);
        em.persist(skill);
        em.persist(experience);
        em.flush();
        em.clear();

        Profile found = em.find(Profile.class, profile.getId());
        assertThat(found.getUserId()).isEqualTo(user.getId());
        assertThat(found.getYearsExperience()).isEqualTo((short) 2);
        assertThat(found.getLanguages()).containsExactly(new LanguageItem("Inglés", "B2"),
                new LanguageItem("Español", "Nativo"));
        assertThat(found.getEducation()).containsExactly(new EducationItem("DAW", "IES Test", 2024));
        assertThat(found.getCvFileName()).isEqualTo("cv.pdf");
        assertThat(found.getCvRawText()).isEqualTo("Texto del CV");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);

        ProfileSkill foundSkill = em.find(ProfileSkill.class, skill.getId());
        assertThat(foundSkill.getProfile().getId()).isEqualTo(profile.getId());
        assertThat(foundSkill.getName()).isEqualTo("Spring Boot");
        assertThat(foundSkill.getLevel()).isEqualTo(SkillLevel.INTERMEDIO);
        assertThat(foundSkill.getYears()).isEqualTo((short) 2);

        Experience foundExperience = em.find(Experience.class, experience.getId());
        assertThat(foundExperience.getProfile().getId()).isEqualTo(profile.getId());
        assertThat(foundExperience.getStartDate()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(foundExperience.getEndDate()).isNull();
        assertThat(foundExperience.getSortOrder()).isEqualTo((short) 0);

        // El JSONB se guarda como array JSON con los nombres de campo de los records.
        assertThat(jdbc.queryForObject("SELECT languages -> 0 ->> 'language' FROM profiles WHERE id = ?",
                String.class, profile.getId())).isEqualTo("Inglés");
    }

    @Test
    void ofertaYRequisitos() {
        User user = persistedUser();
        JobOffer offer = persistedOffer(user);
        OfferRequirement requirement = new OfferRequirement(offer, "Java", RequirementType.OBLIGATORIO,
                RequirementCategory.TECNICO, (short) 0);
        em.persist(requirement);
        em.flush();
        em.clear();

        JobOffer found = em.find(JobOffer.class, offer.getId());
        assertThat(found.getUserId()).isEqualTo(user.getId());
        assertThat(found.getTitle()).isEqualTo("Java Junior");
        assertThat(found.getWorkMode()).isEqualTo(WorkMode.HIBRIDO);
        assertThat(found.getSalaryMin()).isEqualTo(24000);
        assertThat(found.getSalaryMax()).isEqualTo(28000);
        assertThat(found.getCurrency()).isEqualTo("EUR");
        assertThat(found.getSourceUrl()).isEqualTo("https://example.com/oferta");
        assertThat(found.getRawText()).isEqualTo(TestRows.VALID_RAW_TEXT);
        assertThat(found.getExtractionStatus()).isEqualTo(ExtractionStatus.COMPLETADA);
        assertThat(found.getCreatedAt()).isEqualTo(NOW);

        OfferRequirement foundRequirement = em.find(OfferRequirement.class, requirement.getId());
        assertThat(foundRequirement.getOffer().getId()).isEqualTo(offer.getId());
        assertThat(foundRequirement.getType()).isEqualTo(RequirementType.OBLIGATORIO);
        assertThat(foundRequirement.getCategory()).isEqualTo(RequirementCategory.TECNICO);
        assertThat(foundRequirement.getSortOrder()).isEqualTo((short) 0);
    }

    @Test
    void analisisConSugerenciasJsonbYResultadosPorRequisito() {
        User user = persistedUser();
        JobOffer offer = persistedOffer(user);
        Profile profile = persistedProfile(user);
        OfferRequirement requirement = new OfferRequirement(offer, "Java", RequirementType.OBLIGATORIO,
                RequirementCategory.TECNICO, (short) 0);
        em.persist(requirement);
        MatchAnalysis analysis = new MatchAnalysis(offer.getId(), profile.getId(), (short) 85, false, "Resumen",
                List.of("Sugerencia 1", "Sugerencia 2", "Sugerencia 3"), "claude-test", NOW);
        em.persist(analysis);
        RequirementMatch match = new RequirementMatch(analysis, requirement.getId(), MatchResult.PARCIAL, "Evidencia");
        em.persist(match);
        em.flush();
        em.clear();

        MatchAnalysis found = em.find(MatchAnalysis.class, analysis.getId());
        assertThat(found.getOfferId()).isEqualTo(offer.getId());
        assertThat(found.getProfileId()).isEqualTo(profile.getId());
        assertThat(found.getScore()).isEqualTo((short) 85);
        assertThat(found.isHasBlockingGap()).isFalse();
        assertThat(found.getSuggestions()).containsExactly("Sugerencia 1", "Sugerencia 2", "Sugerencia 3");
        assertThat(found.getLlmModel()).isEqualTo("claude-test");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);

        RequirementMatch foundMatch = em.find(RequirementMatch.class, match.getId());
        assertThat(foundMatch.getAnalysisId()).isEqualTo(analysis.getId());
        assertThat(foundMatch.getRequirementId()).isEqualTo(requirement.getId());
        assertThat(foundMatch.getOfferId()).as("el offerId lo aporta el análisis").isEqualTo(offer.getId());
        assertThat(foundMatch.getResult()).isEqualTo(MatchResult.PARCIAL);
        assertThat(foundMatch.getEvidence()).isEqualTo("Evidencia");
    }

    @Test
    void documentoGenerado() {
        JobOffer offer = persistedOffer(persistedUser());
        GeneratedDocument document = new GeneratedDocument(offer.getId(), DocumentType.CV_ADAPTADO, "# CV",
                (short) 2, true, NOW);

        GeneratedDocument found = reload(document, GeneratedDocument.class, document.getId());

        assertThat(found.getOfferId()).isEqualTo(offer.getId());
        assertThat(found.getType()).isEqualTo(DocumentType.CV_ADAPTADO);
        assertThat(found.getContentMarkdown()).isEqualTo("# CV");
        assertThat(found.getVersion()).isEqualTo((short) 2);
        assertThat(found.isEditedByUser()).isTrue();
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void candidaturaConVersionOptimistaEHistorial() {
        User user = persistedUser();
        JobOffer offer = persistedOffer(user);
        Application application = new Application(user.getId(), offer.getId(), ApplicationStatus.ENVIADA, 3,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), "Notas", NOW);
        em.persist(application);
        ApplicationStatusHistory history = new ApplicationStatusHistory(application, ApplicationStatus.GUARDADA,
                ApplicationStatus.ENVIADA, NOW);
        em.persist(history);
        em.flush();
        em.clear();

        Application found = em.find(Application.class, application.getId());
        assertThat(found.getUserId()).isEqualTo(user.getId());
        assertThat(found.getOfferId()).isEqualTo(offer.getId());
        assertThat(found.getStatus()).isEqualTo(ApplicationStatus.ENVIADA);
        assertThat(found.getBoardPosition()).isEqualTo(3);
        assertThat(found.getAppliedAt()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(found.getFollowUpDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(found.getNotes()).isEqualTo("Notas");
        assertThat(found.getVersion()).isZero();
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getUpdatedAt()).isEqualTo(NOW);

        ApplicationStatusHistory foundHistory = em.find(ApplicationStatusHistory.class, history.getId());
        assertThat(foundHistory.getApplication().getId()).isEqualTo(application.getId());
        assertThat(foundHistory.getFromStatus()).isEqualTo(ApplicationStatus.GUARDADA);
        assertThat(foundHistory.getToStatus()).isEqualTo(ApplicationStatus.ENVIADA);
        assertThat(foundHistory.getChangedAt()).isEqualTo(NOW);
    }

    @Test
    void historialDeCreacionSinEstadoDeOrigen() {
        User user = persistedUser();
        Application application = new Application(user.getId(), persistedOffer(user).getId(),
                ApplicationStatus.GUARDADA, 0, null, null, null, NOW);
        em.persist(application);
        ApplicationStatusHistory history = new ApplicationStatusHistory(application, null,
                ApplicationStatus.GUARDADA, NOW);

        ApplicationStatusHistory found = reload(history, ApplicationStatusHistory.class, history.getId());

        assertThat(found.getFromStatus()).isNull();
        assertThat(found.getToStatus()).isEqualTo(ApplicationStatus.GUARDADA);
    }
}
