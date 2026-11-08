package com.jobpilot.ai;

import com.jobpilot.offer.domain.RequirementCategory;
import com.jobpilot.offer.domain.RequirementType;
import com.jobpilot.offer.domain.WorkMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Datos de una oferta extraídos por el LLM ({@link LlmGateway#extractOffer(String)}). Las longitudes máximas son las
 * de las columnas de {@code job_offers} y {@code offer_requirements}: una respuesta que no cabe se rechaza (y se
 * reintenta una vez), nunca se trunca. Las reglas entre campos están en {@link OfferExtractionValidator}.
 *
 * @param title        puesto
 * @param company      empresa, {@code null} si la oferta no la dice
 * @param location     ubicación, {@code null} si la oferta no la dice
 * @param workMode     modalidad; {@code DESCONOCIDO} si la oferta no la dice
 * @param salary       salario bruto anual, {@code null} si no aparece
 * @param requirements requisitos en el orden en que aparecen en la oferta
 */
public record OfferExtraction(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 120) String company,
        @Size(max = 120) String location,
        @NotNull WorkMode workMode,
        @Valid Salary salary,
        @NotNull @Size(min = 1, max = OfferExtraction.MAX_REQUIREMENTS) List<@NotNull @Valid Requirement> requirements) {

    public static final int MAX_REQUIREMENTS = 40;

    public OfferExtraction {
        requirements = requirements == null ? null : List.copyOf(requirements);
    }

    /**
     * Salario bruto anual. Puede faltar un extremo (p. ej. "hasta 40.000 €"), pero no los dos, y la moneda es
     * obligatoria (restricciones de {@code job_offers}).
     */
    public record Salary(
            @PositiveOrZero Integer min,
            @PositiveOrZero Integer max,
            @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {
    }

    /** Requisito de la oferta. */
    public record Requirement(
            @NotBlank @Size(max = 300) String description,
            @NotNull RequirementType type,
            @NotNull RequirementCategory category) {
    }
}
