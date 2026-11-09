package com.jobpilot.analysis.service;

import com.jobpilot.ai.LlmGateway;
import com.jobpilot.ai.MatchEvaluation;
import com.jobpilot.ai.ProfileSnapshot;
import com.jobpilot.ai.RequirementSnapshot;
import com.jobpilot.analysis.domain.MatchResult;
import com.jobpilot.analysis.domain.MatchScoreCalculator;
import com.jobpilot.analysis.domain.RequirementEvaluation;
import com.jobpilot.analysis.domain.ScoreResult;
import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.domain.OfferRequirement;
import com.jobpilot.offer.repository.JobOfferRepository;
import com.jobpilot.offer.repository.OfferRequirementRepository;
import com.jobpilot.profile.domain.Profile;
import com.jobpilot.profile.repository.ExperienceRepository;
import com.jobpilot.profile.repository.ProfileRepository;
import com.jobpilot.profile.repository.ProfileSkillRepository;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.NotFoundException;
import com.jobpilot.shared.ratelimit.RateLimits;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Análisis de encaje (F3, HU-03). Sin {@code @Transactional} a propósito: el LLM tarda segundos y nunca se le espera
 * con una transacción abierta. El flujo es:
 *
 * <ol>
 *   <li>Lecturas (cada una en su propia transacción corta de Spring Data): la oferta del usuario (si no → 404), su
 *       perfil (si no → 409 {@code profile-required}) y los requisitos de la oferta (si no está COMPLETADA o no
 *       tiene → 409 {@code offer-not-extracted}). Los requisitos de una oferta COMPLETADA ya no cambian: reextraer
 *       solo se permite en PENDIENTE o FALLIDA.</li>
 *   <li>Se gasta una petición del cupo de IA (si no queda → 429). Lo que acaba en 404 o 409 no gasta cupo.</li>
 *   <li>Llamada al LLM, fuera de transacción: solo clasifica los requisitos.</li>
 *   <li>{@link MatchScoreCalculator} calcula la puntuación en Java.</li>
 *   <li>Transacción corta: se guardan el análisis y el resultado de cada requisito. Si algo falla antes, no se guarda
 *       nada.</li>
 * </ol>
 */
@Service
public class MatchAnalysisService {

    private final JobOfferRepository offers;
    private final OfferRequirementRepository requirements;
    private final ProfileRepository profiles;
    private final ProfileSkillRepository skills;
    private final ExperienceRepository experiences;
    private final AnalysisPersistence persistence;
    private final LlmGateway llm;
    private final RateLimits rateLimits;
    private final MatchScoreCalculator calculator = new MatchScoreCalculator();

    MatchAnalysisService(JobOfferRepository offers, OfferRequirementRepository requirements,
            ProfileRepository profiles, ProfileSkillRepository skills, ExperienceRepository experiences,
            AnalysisPersistence persistence, LlmGateway llm, RateLimits rateLimits) {
        this.offers = offers;
        this.requirements = requirements;
        this.profiles = profiles;
        this.skills = skills;
        this.experiences = experiences;
        this.persistence = persistence;
        this.llm = llm;
        this.rateLimits = rateLimits;
    }

    /** {@code POST /offers/{offerId}/analysis}: crea un análisis nuevo (se guarda un histórico). */
    public AnalysisDetails analyze(UUID userId, UUID offerId) {
        JobOffer offer = ownOffer(userId, offerId);
        Profile profile = profiles.findByUserId(userId).orElseThrow(MatchAnalysisService::profileRequired);
        List<RequirementSnapshot> offerRequirements = offer.isExtractionCompleted()
                ? requirements.findByOfferIdOrdered(offerId).stream().map(MatchAnalysisService::snapshot).toList()
                : List.of();
        if (offerRequirements.isEmpty()) {
            throw offerNotExtracted();
        }
        ProfileSnapshot profileSnapshot = snapshot(profile);

        rateLimits.consumeAiCall(userId);
        MatchEvaluation evaluation = llm.evaluateMatch(profileSnapshot, offerRequirements);
        ScoreResult score = calculator.calculate(evaluations(offerRequirements, evaluation));

        return persistence.save(offerId, profile.getId(), offerRequirements, evaluation, score, llm.modelName());
    }

    /** {@code GET /offers/{offerId}/analysis}: el análisis más reciente, o 404 si la oferta aún no tiene ninguno. */
    public AnalysisDetails latest(UUID userId, UUID offerId) {
        ownOffer(userId, offerId);
        return persistence.latest(offerId)
                .orElseThrow(() -> new NotFoundException("La oferta todavía no tiene ningún análisis."));
    }

    private JobOffer ownOffer(UUID userId, UUID offerId) {
        return offers.findByIdAndUserId(offerId, userId)
                .orElseThrow(() -> new NotFoundException("No existe la oferta."));
    }

    /** La evaluación del LLM ya está validada: cada requisito aparece exactamente una vez. */
    private static List<RequirementEvaluation> evaluations(List<RequirementSnapshot> offerRequirements,
            MatchEvaluation evaluation) {
        Map<UUID, MatchResult> results = evaluation.matches().stream()
                .collect(Collectors.toMap(MatchEvaluation.Match::requirementId, MatchEvaluation.Match::result));
        return offerRequirements.stream()
                .map(requirement -> new RequirementEvaluation(requirement.type(), results.get(requirement.id())))
                .toList();
    }

    private static RequirementSnapshot snapshot(OfferRequirement requirement) {
        return new RequirementSnapshot(requirement.getId(), requirement.getDescription(), requirement.getType(),
                requirement.getCategory());
    }

    /** Solo el perfil estructurado: ni el texto en bruto del CV ni los datos de la cuenta (sección 2.5). */
    private ProfileSnapshot snapshot(Profile profile) {
        return new ProfileSnapshot(profile.getHeadline(), profile.getSummary(), profile.getYearsExperience(),
                profile.getLocation(),
                map(skills.findByProfileIdOrdered(profile.getId()),
                        skill -> new ProfileSnapshot.Skill(skill.getName(), skill.getLevel(), skill.getYears())),
                map(experiences.findByProfileIdOrdered(profile.getId()),
                        experience -> new ProfileSnapshot.ExperienceItem(experience.getCompany(), experience.getRole(),
                                experience.getStartDate(), experience.getEndDate(), experience.getDescription())),
                profile.getEducation(), profile.getLanguages());
    }

    private static <T, R> List<R> map(List<T> items, Function<T, R> mapper) {
        return items.stream().map(mapper).toList();
    }

    static ConflictException profileRequired() {
        return new ConflictException("profile-required", "Necesitas crear tu perfil",
                "Para calcular el encaje con una oferta, primero crea tu perfil a partir de tu CV.");
    }

    static ConflictException offerNotExtracted() {
        return new ConflictException("offer-not-extracted", "La oferta no tiene requisitos extraídos",
                "Los requisitos de esta oferta no se han podido extraer; reintenta la extracción antes de calcular "
                        + "el encaje.");
    }
}
