package com.jobpilot.document.service;

import com.jobpilot.ai.DocumentContext;
import com.jobpilot.ai.GeneratedDocumentContent;
import com.jobpilot.ai.LlmGateway;
import com.jobpilot.ai.ProfileSnapshot;
import com.jobpilot.analysis.domain.MatchAnalysis;
import com.jobpilot.analysis.repository.MatchAnalysisRepository;
import com.jobpilot.analysis.repository.RequirementMatchRepository;
import com.jobpilot.auth.repository.UserRepository;
import com.jobpilot.document.domain.DocumentType;
import com.jobpilot.document.domain.GeneratedDocument;
import com.jobpilot.document.repository.GeneratedDocumentRepository;
import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.repository.JobOfferRepository;
import com.jobpilot.profile.domain.Profile;
import com.jobpilot.profile.repository.ExperienceRepository;
import com.jobpilot.profile.repository.ProfileRepository;
import com.jobpilot.profile.repository.ProfileSkillRepository;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.NotFoundException;
import com.jobpilot.shared.ratelimit.RateLimits;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Carta y CV adaptado (F4, HU-04). Sin {@code @Transactional} a propósito: el LLM tarda segundos y nunca se le espera
 * con una transacción abierta. La generación sigue el flujo del análisis:
 *
 * <ol>
 *   <li>Lecturas (cada una en su transacción corta de Spring Data): la oferta del usuario (si no → 404), su último
 *       análisis (si no → 409 {@code analysis-required}), los resultados de ese análisis y el perfil.</li>
 *   <li>Se gasta una petición del cupo de IA (si no queda → 429). Lo que acaba en 404 o 409 no gasta cupo.</li>
 *   <li>Llamada al LLM, fuera de transacción, sin el nombre ni el email del usuario.</li>
 *   <li>La carta se firma: el servidor añade el {@code full_name} debajo de la despedida.</li>
 *   <li>Transacción corta: se guarda como la versión siguiente a la más alta. Si otra escritura se ha quedado esa
 *       versión entre medias, se recalcula una vez (sin volver a llamar al LLM, que ya se ha pagado); si vuelve a
 *       chocar, 409 {@code document-version-conflict}.</li>
 * </ol>
 */
@Service
public class DocumentService {

    private static final String MARKDOWN_SPECIAL = "\\`*_[]()#";

    private final JobOfferRepository offers;
    private final MatchAnalysisRepository analyses;
    private final RequirementMatchRepository matches;
    private final ProfileRepository profiles;
    private final ProfileSkillRepository skills;
    private final ExperienceRepository experiences;
    private final UserRepository users;
    private final GeneratedDocumentRepository documents;
    private final DocumentPersistence persistence;
    private final LlmGateway llm;
    private final RateLimits rateLimits;
    private final PdfRenderer pdfRenderer;

    DocumentService(JobOfferRepository offers, MatchAnalysisRepository analyses, RequirementMatchRepository matches,
            ProfileRepository profiles, ProfileSkillRepository skills, ExperienceRepository experiences,
            UserRepository users, GeneratedDocumentRepository documents, DocumentPersistence persistence,
            LlmGateway llm, RateLimits rateLimits, PdfRenderer pdfRenderer) {
        this.offers = offers;
        this.analyses = analyses;
        this.matches = matches;
        this.profiles = profiles;
        this.skills = skills;
        this.experiences = experiences;
        this.users = users;
        this.documents = documents;
        this.persistence = persistence;
        this.llm = llm;
        this.rateLimits = rateLimits;
        this.pdfRenderer = pdfRenderer;
    }

    /** {@code POST /offers/{offerId}/documents}: una versión nueva generada por la IA. */
    public GeneratedDocument generate(UUID userId, UUID offerId, DocumentType type) {
        JobOffer offer = ownOffer(userId, offerId);
        MatchAnalysis analysis = analyses.findFirstByOfferIdOrderByCreatedAtDescIdDesc(offerId)
                .orElseThrow(DocumentService::analysisRequired);
        Profile profile = profiles.findByUserId(userId).orElseThrow(DocumentService::profileRequired);
        DocumentContext context = new DocumentContext(type,
                new DocumentContext.OfferData(offer.getTitle(), offer.getCompany(), offer.getLocation(),
                        offer.getWorkMode(), offer.getRawText()),
                ProfileSnapshot.from(profile, skills.findByProfileIdOrdered(profile.getId()),
                        experiences.findByProfileIdOrdered(profile.getId())),
                analysis.getSummary(), requirementResults(analysis));
        String signature = type == DocumentType.CARTA ? fullName(userId) : null;

        rateLimits.consumeAiCall(userId);
        GeneratedDocumentContent generated = llm.generateDocument(context);
        String content = signature == null ? generated.contentMarkdown() : sign(generated.contentMarkdown(), signature);

        try {
            return persistence.saveGenerated(offerId, type, content);
        } catch (ConflictException e) {
            if (!DocumentPersistence.VERSION_CONFLICT.equals(e.getTypeSlug())) {
                throw e;
            }
            return persistence.saveGenerated(offerId, type, content);
        }
    }

    /** {@code GET /offers/{offerId}/documents?type=}: la última versión y el historial; 404 si no hay ninguna. */
    public DocumentHistory history(UUID userId, UUID offerId, DocumentType type) {
        ownOffer(userId, offerId);
        return persistence.history(offerId, type)
                .orElseThrow(() -> new NotFoundException("La oferta todavía no tiene ningún documento de ese tipo."));
    }

    /** {@code POST /documents/{documentId}/versions}: la edición del usuario como versión nueva. */
    public GeneratedDocument createVersion(UUID userId, UUID documentId, String contentMarkdown) {
        return persistence.saveEdit(ownDocument(userId, documentId), contentMarkdown);
    }

    /** {@code GET /documents/{documentId}/pdf}: esa versión en PDF, con el nombre del usuario en la cabecera. */
    public DocumentPdf pdf(UUID userId, UUID documentId) {
        GeneratedDocument document = ownDocument(userId, documentId);
        byte[] content = pdfRenderer.render(fullName(userId), document.getContentMarkdown());
        return new DocumentPdf(fileName(document), content);
    }

    /**
     * La despedida de la carta con el nombre debajo, en un párrafo propio. El nombre se escapa: lo escribe el usuario
     * al registrarse y debe verse literal, no como formato ni como enlace.
     */
    static String sign(String letter, String fullName) {
        return letter.stripTrailing() + "\n\n" + escapeMarkdown(fullName);
    }

    /** Antepone {@code \} a los caracteres con significado en Markdown: {@code \ ` * _ [ ] ( ) #}. */
    static String escapeMarkdown(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (MARKDOWN_SPECIAL.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private static String fileName(GeneratedDocument document) {
        String prefix = document.getType() == DocumentType.CARTA ? "carta" : "cv-adaptado";
        return prefix + "-v" + document.getVersion() + ".pdf";
    }

    private List<DocumentContext.RequirementResult> requirementResults(MatchAnalysis analysis) {
        return matches.findViewsByAnalysisId(analysis.getId()).stream()
                .map(match -> new DocumentContext.RequirementResult(match.description(), match.type(),
                        match.result(), match.evidence()))
                .toList();
    }

    private String fullName(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("No existe el usuario."))
                .getFullName().strip();
    }

    private JobOffer ownOffer(UUID userId, UUID offerId) {
        return offers.findByIdAndUserId(offerId, userId)
                .orElseThrow(() -> new NotFoundException("No existe la oferta."));
    }

    private GeneratedDocument ownDocument(UUID userId, UUID documentId) {
        return documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new NotFoundException("No existe el documento."));
    }

    static ConflictException analysisRequired() {
        return new ConflictException("analysis-required", "La oferta no tiene análisis de encaje",
                "Para generar la carta o el CV adaptado, primero calcula el encaje con esta oferta.");
    }

    /** No debería pasar (un análisis exige perfil y el perfil no se borra), pero se responde como en el análisis. */
    static ConflictException profileRequired() {
        return new ConflictException("profile-required", "Necesitas crear tu perfil",
                "Para generar la carta o el CV adaptado, primero crea tu perfil a partir de tu CV.");
    }
}
