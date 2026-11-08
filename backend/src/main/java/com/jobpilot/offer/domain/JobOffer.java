package com.jobpilot.offer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Oferta de empleo importada (tabla {@code job_offers}). El texto en bruto se guarda siempre, aunque la extracción
 * con IA falle. {@code sourceUrl} solo es una referencia: el backend nunca la descarga.
 */
@Entity
@Table(name = "job_offers")
public class JobOffer {

    @Id
    private UUID id;

    private UUID userId;

    private String title;

    private String company;

    private String location;

    @Enumerated(EnumType.STRING)
    private WorkMode workMode;

    private Integer salaryMin;

    private Integer salaryMax;

    private String currency;

    private String sourceUrl;

    private String rawText;

    @Enumerated(EnumType.STRING)
    private ExtractionStatus extractionStatus;

    private Instant createdAt;

    protected JobOffer() {
    }

    public JobOffer(UUID userId, String title, String company, String location, WorkMode workMode,
            Integer salaryMin, Integer salaryMax, String currency, String sourceUrl, String rawText,
            ExtractionStatus extractionStatus, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.title = title;
        this.company = company;
        this.location = location;
        this.workMode = workMode;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.currency = currency;
        this.sourceUrl = sourceUrl;
        this.rawText = rawText;
        this.extractionStatus = extractionStatus;
        this.createdAt = createdAt;
    }

    /** Oferta recién importada, antes de llamar al LLM: solo el texto, la URL de referencia y la fecha. */
    public static JobOffer pending(UUID userId, String rawText, String sourceUrl, Instant createdAt) {
        return new JobOffer(userId, null, null, null, WorkMode.DESCONOCIDO, null, null, null, sourceUrl, rawText,
                ExtractionStatus.PENDIENTE, createdAt);
    }

    /** Guarda los datos que ha extraído el LLM. Los requisitos van en su propia tabla. */
    public void completeExtraction(String title, String company, String location, WorkMode workMode,
            Integer salaryMin, Integer salaryMax, String currency) {
        if (isExtractionCompleted()) {
            throw new IllegalStateException("La extracción de la oferta " + id + " ya está completada");
        }
        this.title = title;
        this.company = company;
        this.location = location;
        this.workMode = workMode;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.currency = currency;
        this.extractionStatus = ExtractionStatus.COMPLETADA;
    }

    /** El LLM no ha podido extraer los datos; el usuario puede reintentarlo. No afecta a una oferta ya completada. */
    public void failExtraction() {
        if (!isExtractionCompleted()) {
            this.extractionStatus = ExtractionStatus.FALLIDA;
        }
    }

    /** Solo una oferta PENDIENTE o FALLIDA admite (re)intentar la extracción. */
    public boolean isExtractionCompleted() {
        return extractionStatus == ExtractionStatus.COMPLETADA;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getLocation() {
        return location;
    }

    public WorkMode getWorkMode() {
        return workMode;
    }

    public Integer getSalaryMin() {
        return salaryMin;
    }

    public Integer getSalaryMax() {
        return salaryMax;
    }

    public String getCurrency() {
        return currency;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getRawText() {
        return rawText;
    }

    public ExtractionStatus getExtractionStatus() {
        return extractionStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
