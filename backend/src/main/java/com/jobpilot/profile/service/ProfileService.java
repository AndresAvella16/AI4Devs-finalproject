package com.jobpilot.profile.service;

import com.jobpilot.ai.LlmGateway;
import com.jobpilot.ai.ProfileExtraction;
import com.jobpilot.profile.repository.ProfileRepository;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.InvalidRequestException;
import com.jobpilot.shared.error.NotFoundException;
import com.jobpilot.shared.ratelimit.RateLimits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Perfil a partir del CV (F1, HU-01). Sin {@code @Transactional} a propósito: el LLM tarda segundos y nunca se le
 * espera con una transacción abierta.
 *
 * <p>Subida del CV ({@code POST /profile/cv}):
 * <ol>
 *   <li>{@link CvTextExtractor} valida el fichero y extrae su texto, en memoria. Lo que acaba en 400, 415 o 422 no
 *       llega a la IA ni gasta cupo.</li>
 *   <li>Se gasta una petición del cupo de IA del usuario (si no queda → 429).</li>
 *   <li>Llamada al LLM, fuera de transacción: solo recibe el texto del CV.</li>
 *   <li>El texto y el nombre del fichero se guardan en {@link CvDraftStore} (en memoria, 30 min). La BD no se toca:
 *       el borrador no es el perfil hasta que el usuario lo confirma.</li>
 * </ol>
 *
 * <p>Guardado ({@code PUT /profile}): las reglas que Bean Validation no comprueba, y después una transacción corta
 * que crea o reemplaza el perfil y le añade el CV del borrador, si sigue vivo.
 */
@Service
public class ProfileService {

    static final String DEFAULT_FILE_NAME = "cv.pdf";
    static final int MAX_FILE_NAME = 255;

    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");

    private final CvTextExtractor extractor;
    private final CvDraftStore drafts;
    private final ProfilePersistence persistence;
    private final ProfileRepository profiles;
    private final LlmGateway llm;
    private final RateLimits rateLimits;

    ProfileService(CvTextExtractor extractor, CvDraftStore drafts, ProfilePersistence persistence,
            ProfileRepository profiles, LlmGateway llm, RateLimits rateLimits) {
        this.extractor = extractor;
        this.drafts = drafts;
        this.persistence = persistence;
        this.profiles = profiles;
        this.llm = llm;
        this.rateLimits = rateLimits;
    }

    /** {@code POST /profile/cv}: devuelve el borrador del perfil sin guardarlo. */
    public CvDraftResult uploadCv(UUID userId, String fileName, String contentType, byte[] content) {
        String text = extractor.extract(contentType, content);
        rateLimits.consumeAiCall(userId);
        ProfileExtraction extraction = llm.extractProfile(text);
        String cleanName = cleanFileName(fileName);
        drafts.put(userId, cleanName, text);
        return new CvDraftResult(cleanName, draft(extraction));
    }

    /** {@code GET /profile}: el perfil del usuario, o 404 si aún no tiene. */
    public ProfileDetails get(UUID userId) {
        return persistence.details(userId)
                .orElseThrow(() -> new NotFoundException("Todavía no has creado tu perfil."));
    }

    /**
     * {@code PUT /profile}: crea el perfil o lo reemplaza. Funciona igual sin borrador del CV (perfil hecho a mano o
     * borrador caducado). El borrador solo se olvida si el perfil se ha guardado.
     *
     * @throws InvalidRequestException si hay habilidades repetidas o una experiencia acaba antes de empezar
     * @throws ConflictException       {@code profile-concurrent-update} si otra petición ha guardado el perfil a la
     *                                 vez y la BD ha rechazado esta escritura
     */
    public SavedProfile save(UUID userId, ProfileData data) {
        checkRules(data);
        Optional<CvDraft> draft = drafts.find(userId);
        SavedProfile saved;
        try {
            saved = persistence.save(userId, data, draft.orElse(null));
        } catch (DataIntegrityViolationException e) {
            // Las reglas de la BD que dependen de los datos ya se han comprobado: si ahora existe el perfil, es que
            // otra petición lo ha creado (uq_profiles_user_id) o reemplazado a la vez.
            if (profiles.findByUserId(userId).isPresent()) {
                throw ConflictException.profileConcurrentUpdate();
            }
            throw e;
        }
        draft.ifPresent(used -> drafts.remove(userId, used));
        return saved;
    }

    /** Las reglas entre elementos de {@code ProfileRequest}; los índices son los de la petición. */
    private static void checkRules(ProfileData data) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < data.skills().size(); i++) {
            if (!names.add(skillKey(data.skills().get(i).name()))) {
                throw new InvalidRequestException("skills[" + i + "].name",
                        "La habilidad está repetida (no se distinguen mayúsculas).");
            }
        }
        for (int i = 0; i < data.experiences().size(); i++) {
            ProfileData.Experience experience = data.experiences().get(i);
            if (experience.endDate() != null && experience.endDate().isBefore(experience.startDate())) {
                throw new InvalidRequestException("experiences[" + i + "].endDate",
                        "La fecha de fin no puede ser anterior a la de inicio.");
            }
        }
    }

    /** El borrador para el formulario: las habilidades repetidas que haya devuelto la IA se quitan. */
    private static ProfileData draft(ProfileExtraction extraction) {
        Set<String> names = new HashSet<>();
        List<ProfileData.Skill> skills = new ArrayList<>();
        for (ProfileExtraction.Skill skill : extraction.skills()) {
            if (names.add(skillKey(skill.name()))) {
                skills.add(new ProfileData.Skill(skill.name().strip(), skill.level(), skill.years()));
            }
        }
        List<ProfileData.Experience> experiences = extraction.experiences().stream()
                .map(e -> new ProfileData.Experience(e.company(), e.role(), e.startDate(), e.endDate(),
                        e.description()))
                .toList();
        return new ProfileData(extraction.headline(), extraction.summary(), extraction.yearsExperience(),
                extraction.location(), extraction.languages(), extraction.education(), skills, experiences);
    }

    private static String skillKey(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Nombre del PDF tal como se guarda en {@code cv_file_name}: sin la ruta que mandan algunos navegadores, sin
     * caracteres de control y como mucho 255 caracteres; {@code cv.pdf} si no queda nada.
     */
    static String cleanFileName(String original) {
        if (original == null) {
            return DEFAULT_FILE_NAME;
        }
        String name = CONTROL_CHARS.matcher(original).replaceAll("");
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).strip();
        if (name.isEmpty()) {
            return DEFAULT_FILE_NAME;
        }
        return name.length() > MAX_FILE_NAME ? name.substring(0, MAX_FILE_NAME) : name;
    }
}
