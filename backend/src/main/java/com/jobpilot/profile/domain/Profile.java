package com.jobpilot.profile.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Perfil profesional del usuario (tabla {@code profiles}). Idiomas y formación van en JSONB: solo se muestran y se
 * envían como contexto al LLM. La forma de cada elemento la validan los records {@link LanguageItem} y
 * {@link EducationItem}.
 */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    private UUID id;

    private UUID userId;

    private String headline;

    private String summary;

    private Short yearsExperience;

    private String location;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<LanguageItem> languages;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<EducationItem> education;

    private String cvFileName;

    private String cvRawText;

    private Instant createdAt;

    private Instant updatedAt;

    protected Profile() {
    }

    public Profile(UUID userId, String headline, String summary, Short yearsExperience, String location,
            List<LanguageItem> languages, List<EducationItem> education, String cvFileName, String cvRawText,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.headline = headline;
        this.summary = summary;
        this.yearsExperience = yearsExperience;
        this.location = location;
        this.languages = List.copyOf(languages);
        this.education = List.copyOf(education);
        this.cvFileName = cvFileName;
        this.cvRawText = cvRawText;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Sustituye el contenido del perfil por el que ha revisado el usuario ({@code PUT /profile}). El id no cambia,
     * así que los análisis que ya apuntan a este perfil se conservan. El CV se mantiene: solo lo cambia
     * {@link #attachCv(String, String, Instant)}.
     */
    public void replace(String headline, String summary, Short yearsExperience, String location,
            List<LanguageItem> languages, List<EducationItem> education, Instant now) {
        this.headline = headline;
        this.summary = summary;
        this.yearsExperience = yearsExperience;
        this.location = location;
        this.languages = List.copyOf(languages);
        this.education = List.copyOf(education);
        this.updatedAt = now;
    }

    /** Asocia el texto de un CV recién subido al perfil (el PDF no se guarda). */
    public void attachCv(String cvFileName, String cvRawText, Instant now) {
        this.cvFileName = cvFileName;
        this.cvRawText = cvRawText;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getHeadline() {
        return headline;
    }

    public String getSummary() {
        return summary;
    }

    public Short getYearsExperience() {
        return yearsExperience;
    }

    public String getLocation() {
        return location;
    }

    public List<LanguageItem> getLanguages() {
        return List.copyOf(languages);
    }

    public List<EducationItem> getEducation() {
        return List.copyOf(education);
    }

    public String getCvFileName() {
        return cvFileName;
    }

    public String getCvRawText() {
        return cvRawText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
