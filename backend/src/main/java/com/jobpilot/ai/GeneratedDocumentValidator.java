package com.jobpilot.ai;

import com.jobpilot.document.domain.DocumentType;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reglas de {@link GeneratedDocumentContent} que Bean Validation no expresa. La carta debe tener entre
 * {@value #MIN_LETTER_WORDS} y {@value #MAX_LETTER_WORDS} palabras: el prompt pide 250–400 (HU-04), y el margen evita
 * pagar un reintento (o dar un 502) por una carta de 245. El CV adaptado no tiene límite de palabras. Los problemas
 * solo dicen el recuento, nunca el texto.
 */
class GeneratedDocumentValidator implements ResponseValidator<GeneratedDocumentContent> {

    static final int MIN_LETTER_WORDS = 200;
    static final int MAX_LETTER_WORDS = 500;

    /** Una palabra es un trozo sin espacios con al menos una letra o un número: "#", "-" o "—" no cuentan. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern HAS_LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");

    private final DocumentType type;

    GeneratedDocumentValidator(DocumentType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public List<String> validate(GeneratedDocumentContent content) {
        if (type != DocumentType.CARTA) {
            return List.of();
        }
        int words = countWords(content.contentMarkdown());
        if (words < MIN_LETTER_WORDS || words > MAX_LETTER_WORDS) {
            return List.of("contentMarkdown: la carta tiene " + words + " palabras (deben ser entre "
                    + MIN_LETTER_WORDS + " y " + MAX_LETTER_WORDS + ")");
        }
        return List.of();
    }

    /** Palabras del texto, sin contar los símbolos sueltos de Markdown ni la puntuación. */
    static int countWords(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        return (int) WHITESPACE.splitAsStream(markdown.strip())
                .filter(token -> HAS_LETTER_OR_DIGIT.matcher(token).find())
                .count();
    }
}
