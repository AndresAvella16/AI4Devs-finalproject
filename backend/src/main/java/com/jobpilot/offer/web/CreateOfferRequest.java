package com.jobpilot.offer.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

/**
 * Cuerpo de {@code POST /api/v1/offers} (schema {@code CreateOfferRequest} de la sección 4).
 *
 * @param rawText   texto de la oferta. La longitud se cuenta en caracteres Unicode, igual que el {@code CHECK} de la
 *                  BD ({@code char_length}): un emoji cuenta como 1, no como 2 unidades UTF-16
 * @param sourceUrl URL de referencia, opcional; nunca se descarga. Solo {@code http} o {@code https}, porque el
 *                  frontend la muestra como enlace (una URL {@code javascript:} sería un XSS). Vacía = sin URL
 */
public record CreateOfferRequest(
        @NotNull
        @CodePointLength(min = 200, max = 20_000, message = "debe tener entre 200 y 20.000 caracteres")
        String rawText,

        @Size(max = 2048)
        @Pattern(regexp = "^$|(?i)https?://[^\\s/?#]+\\S*", message = "debe ser una URL http o https")
        String sourceUrl) {
}
