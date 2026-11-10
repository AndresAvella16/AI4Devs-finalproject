package com.jobpilot.e2e.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CV de ejemplo para el E2E (JP-FE-02): un PDF de una página con texto, generado en memoria. Todos los datos son
 * ficticios. Con {@code LLM_MODE=fake} el contenido da igual (el borrador es siempre el de
 * {@code fake-llm/cv-extraction.json}), pero el backend extrae su texto con PDFBox, así que tiene que ser un PDF
 * real con texto: si no, respondería 422 {@code cv-without-text}.
 *
 * <p>Se escribe a mano (catálogo, página, fuente Helvetica estándar, contenido y tabla {@code xref}) para no añadir
 * una dependencia ni un fichero binario al repositorio. Solo admite texto ASCII (la fuente estándar sin
 * codificación propia).
 */
public final class SampleCv {

    public static final String FILE_NAME = "cv-ficticio-e2e.pdf";

    private static final List<String> LINES = List.of(
            "Candidata Ficticia E2E",
            "Desarrolladora Web Junior - Java",
            "Valencia (dato inventado para pruebas)",
            "",
            "Experiencia",
            "Tecnologias Ficticias S.L. - Desarrolladora Junior (2025 - actualidad)",
            "APIs REST con Java 21 y Spring Boot sobre PostgreSQL. Tests con JUnit.",
            "",
            "Formacion",
            "CFGS Desarrollo de Aplicaciones Web (DAW), IES Ficticio, 2024",
            "",
            "Habilidades: Java, Spring Boot, PostgreSQL, Git, React",
            "Idiomas: Espanol (nativo), Ingles (B2)");

    private SampleCv() {
    }

    /** Los bytes del PDF. */
    public static byte[] pdf() {
        StringBuilder content = new StringBuilder("BT\n/F1 12 Tf\n16 TL\n72 770 Td\n");
        for (String line : LINES) {
            content.append('(').append(escape(line)).append(") Tj T*\n");
        }
        content.append("ET\n");
        byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> "
                        + "/Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Length " + stream.length + " >>\nstream\n" + new String(stream, StandardCharsets.US_ASCII)
                        + "endstream");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        offsets.forEach(offset -> table.append(String.format("%010d 00000 n \n", offset)));
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        write(out, table.toString());
        return out.toByteArray();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
    }
}
