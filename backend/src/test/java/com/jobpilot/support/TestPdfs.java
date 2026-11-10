package com.jobpilot.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * PDFs de prueba generados con PDFBox en el propio test, para no versionar ficheros binarios. Solo usan la fuente
 * estándar Helvetica (WinAnsi): los textos admiten tildes y eñes, pero no emojis.
 */
public final class TestPdfs {

    /** Texto de un CV de ejemplo, con más de 50 caracteres útiles. */
    public static final String SAMPLE_CV_TEXT = "Laura Ficticia. Desarrolladora Web Junior en Valencia. "
            + "Experiencia con Java 21, Spring Boot y PostgreSQL. Inglés B2.";

    private TestPdfs() {
    }

    /** Un PDF con una página por cada texto; cada línea del texto ({@code \n}) va en su propia línea. */
    public static byte[] withText(String... pages) {
        try (PDDocument document = new PDDocument()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    content.setLeading(14);
                    content.newLineAtOffset(50, 740);
                    for (String line : text.split("\n")) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Un CV de ejemplo de una página. */
    public static byte[] sampleCv() {
        return withText(SAMPLE_CV_TEXT);
    }

    /** Páginas sin texto, como un PDF escaneado (solo imágenes). */
    public static byte[] blankPages(int count) {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < count; i++) {
                document.addPage(new PDPage());
            }
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Un PDF con texto y {@code count} páginas. */
    public static byte[] pages(int count) {
        String[] texts = new String[count];
        for (int i = 0; i < count; i++) {
            texts[i] = "Página " + (i + 1) + ". " + SAMPLE_CV_TEXT;
        }
        return withText(texts);
    }

    /** Un CV protegido con contraseña de apertura: no se puede leer sin ella. */
    public static byte[] encrypted() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret",
                    new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
