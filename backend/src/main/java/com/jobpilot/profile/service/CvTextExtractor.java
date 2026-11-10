package com.jobpilot.profile.service;

import com.jobpilot.shared.error.CvWithoutTextException;
import com.jobpilot.shared.error.InvalidCvFileException;
import com.jobpilot.shared.error.InvalidRequestException;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Extrae el texto de un CV en PDF con Apache PDFBox (F1, sección 2.5). El fichero nunca se escribe en disco: se lee
 * en memoria, se extrae su texto y se descarta. Las comprobaciones van de la más barata a la más cara, y todas se
 * hacen antes de llamar a la IA:
 *
 * <ol>
 *   <li>Tamaño: vacío o más de 5 MB → 400 {@code validation-error} en {@code file}.</li>
 *   <li>Tipo MIME {@code application/pdf} y firma {@code %PDF-} → si no, 415 {@code invalid-cv-file}.</li>
 *   <li>PDFBox lo abre (si está corrupto o tiene contraseña de apertura → 415) y tiene como mucho 10 páginas
 *       (si no → 400), para acotar la memoria y el tiempo de la extracción.</li>
 *   <li>Texto: menos de 50 caracteres útiles → 422 {@code cv-without-text} (PDF escaneado); más de 50.000 → 400.
 *       Nunca se trunca: lo que llega a la IA es el CV entero.</li>
 * </ol>
 *
 * <p>El texto no se escribe en el log: lleva datos personales.
 */
@Component
public class CvTextExtractor {

    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_PAGES = 10;
    public static final int MIN_TEXT_CHARS = 50;
    public static final int MAX_TEXT_CHARS = 50_000;

    static final String FIELD = "file";

    private static final String PDF_MIME = "application/pdf";
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};

    /** Caracteres de control, salvo el salto de línea y el tabulador (PostgreSQL no admite {@code \u0000}). */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t]+(?=\\n)");
    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private final int maxFileBytes;
    private final int maxPages;
    private final int minTextChars;
    private final int maxTextChars;

    public CvTextExtractor() {
        this(MAX_FILE_BYTES, MAX_PAGES, MIN_TEXT_CHARS, MAX_TEXT_CHARS);
    }

    CvTextExtractor(int maxFileBytes, int maxPages, int minTextChars, int maxTextChars) {
        this.maxFileBytes = maxFileBytes;
        this.maxPages = maxPages;
        this.minTextChars = minTextChars;
        this.maxTextChars = maxTextChars;
    }

    /**
     * @param contentType tipo MIME que declara el cliente
     * @param content     bytes del fichero
     * @return el texto del CV, limpio (ver {@link #clean(String)})
     * @throws InvalidRequestException si está vacío, pesa más de 5 MB, tiene más de 10 páginas o demasiado texto
     * @throws InvalidCvFileException  si no es un PDF que se pueda leer
     * @throws CvWithoutTextException  si no tiene texto (escaneado)
     */
    public String extract(String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidRequestException(FIELD, "Sube tu CV en PDF.");
        }
        if (content.length > maxFileBytes) {
            throw new InvalidRequestException(FIELD, InvalidCvFileException.DETAIL);
        }
        if (!isPdfMime(contentType) || !hasPdfSignature(content)) {
            throw new InvalidCvFileException();
        }
        String text = clean(readText(content));
        if (WHITESPACE.matcher(text).replaceAll("").length() < minTextChars) {
            throw new CvWithoutTextException();
        }
        if (text.length() > maxTextChars) {
            throw new InvalidRequestException(FIELD, "El CV tiene demasiado texto (máximo "
                    + String.format(Locale.forLanguageTag("es-ES"), "%,d", maxTextChars) + " caracteres).");
        }
        return text;
    }

    private String readText(byte[] content) {
        // Solo memoria: ni el PDF ni sus partes se escriben en ficheros temporales.
        try (PDDocument document = Loader.loadPDF(content, "", null, null, IOUtils.createMemoryOnlyStreamCache())) {
            if (document.getNumberOfPages() == 0) {
                // El parser de PDFBox es tolerante: un fichero que solo tiene la cabecera se abre sin páginas.
                throw new InvalidCvFileException();
            }
            if (document.getNumberOfPages() > maxPages) {
                throw new InvalidRequestException(FIELD, "El CV no puede tener más de " + maxPages + " páginas.");
            }
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            // Corrupto, truncado o con contraseña de apertura (InvalidPasswordException es una IOException).
            throw new InvalidCvFileException();
        }
    }

    /**
     * Quita los caracteres de control (salvo saltos de línea y tabuladores), normaliza los saltos de línea, quita
     * los espacios al final de cada línea, deja como mucho una línea en blanco seguida y recorta el principio y el
     * final.
     */
    static String clean(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = CONTROL_CHARS.matcher(normalized).replaceAll("");
        normalized = TRAILING_SPACES.matcher(normalized).replaceAll("");
        normalized = EXTRA_BLANK_LINES.matcher(normalized).replaceAll("\n\n");
        return normalized.strip();
    }

    private static boolean isPdfMime(String contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.split(";", 2)[0].strip();
        return type.equalsIgnoreCase(PDF_MIME);
    }

    private static boolean hasPdfSignature(byte[] content) {
        if (content.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PDF_SIGNATURE.length; i++) {
            if (content[i] != PDF_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
}
