package com.jobpilot.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exportación a PDF (HU-04, escenario "Exportación"): el PDF lleva el contenido de la versión y el nombre del usuario
 * en la cabecera. Seguridad (sección 2.5): el HTML del Markdown se escapa, las imágenes no se cargan, los enlaces
 * {@code javascript:} se quitan y el motor del PDF no accede a ningún recurso externo (http, https, file, data).
 */
class PdfRendererTest {

    private static final String NAME = "Lucía Ferrándiz Núñez";

    /** PNG de 1×1 píxel válido. */
    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private final PdfRenderer renderer = new PdfRenderer();

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @TempDir
    Path tempDir;

    @BeforeEach
    void localServerThatCountsRequests() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] png = java.util.Base64.getDecoder().decode(PNG_BASE64);
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String serverUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);   // en orden de lectura: la negrita se dibuja aparte
            return stripper.getText(document);
        }
    }

    private static int images(byte[] pdf) throws IOException {
        int count = 0;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) {
                    continue;
                }
                for (COSName name : resources.getXObjectNames()) {
                    if (resources.getXObject(name) instanceof PDImageXObject) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static List<String> linkUris(byte[] pdf) throws IOException {
        List<String> uris = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink link) {
                        PDAction action = link.getAction();
                        if (action instanceof PDActionURI uri) {
                            uris.add(uri.getURI());
                        } else if (action != null) {
                            uris.add(action.getType());
                        }
                    }
                }
            }
        }
        return uris;
    }

    // ---- Contenido ----

    @Test
    void elPdfEsValidoYLlevaElNombreEnLaCabeceraYElContenido() throws IOException {
        byte[] pdf = renderer.render(NAME, """
                # Carta de presentación

                Me interesa el puesto de **Desarrollador/a Java Junior**: año, señal, 25.000 €.

                - Java y Spring Boot
                - PostgreSQL

                Atentamente,

                Lucía Ferrándiz Núñez""");

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text.strip()).as("el nombre va primero, en la cabecera").startsWith(NAME);
        assertThat(text).contains("Carta de presentación")
                .contains("Desarrollador/a Java Junior")
                .contains("año, señal, 25.000 €")
                .contains("Java y Spring Boot").contains("PostgreSQL")
                .contains("Atentamente,");
        assertThat(text).as("el Markdown se interpreta, no se copia").doesNotContain("**").doesNotContain("# Carta");
    }

    @Test
    void elNombreSeEscapaEnLaCabecera() throws IOException {
        byte[] pdf = renderer.render("<b>Ana</b> & Co", "Texto");

        assertThat(text(pdf)).contains("<b>Ana</b> & Co");
        assertThat(renderer.html("<b>Ana</b> & Co", "Texto")).doesNotContain("<b>Ana");
    }

    @Test
    void unaFirmaConMarkdownEnElNombreSaleLiteralEnElPdf() throws IOException {
        String name = "Ana *Pérez* [x](" + serverUrl("/firma") + ")";
        String markdown = DocumentService.sign("Me interesa el puesto.\n\nAtentamente,", name);

        assertThat(renderer.html(name, markdown)).doesNotContain("<em>").doesNotContain("<a ");
        byte[] pdf = renderer.render(name, markdown);

        String text = text(pdf);
        assertThat(text.indexOf(name)).as("en la cabecera").isGreaterThanOrEqualTo(0);
        assertThat(text.lastIndexOf(name)).as("y como firma, literal").isGreaterThan(text.indexOf("Atentamente,"));
        assertThat(linkUris(pdf)).isEmpty();
        assertThat(requests.get()).isZero();
    }

    @Test
    void unContenidoLargoOcupaVariasPaginas() throws IOException {
        byte[] pdf = renderer.render(NAME, "Párrafo de prueba con bastante texto.\n\n".repeat(300));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
    }

    // ---- Seguridad: HTML crudo ----

    @Test
    void unScriptSeMuestraComoTextoYNoSeEjecuta() throws IOException {
        String markdown = "Hola <script>alert('xss')</script> adiós";

        assertThat(renderer.html(NAME, markdown)).doesNotContain("<script");
        byte[] pdf = renderer.render(NAME, markdown);
        assertThat(text(pdf)).contains("<script>alert('xss')</script>");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getDocumentCatalog().getNames()).as("sin JavaScript en el PDF").isNull();
            assertThat(document.getDocumentCatalog().getCOSObject().getItem(COSName.OPEN_ACTION)).isNull();
        }
    }

    @Test
    void unaImagenHtmlRemotaNoSeDescarga() throws IOException {
        String markdown = "Foto: <img src=\"" + serverUrl("/html.png") + "\">\n\n<div><img src=\""
                + serverUrl("/block.png") + "\"></div>";

        assertThat(renderer.html(NAME, markdown)).doesNotContain("<img").doesNotContain("<div><")
                .contains("&lt;img src=").contains("&lt;div&gt;");
        byte[] pdf = renderer.render(NAME, markdown);

        assertThat(requests.get()).as("peticiones al servidor").isZero();
        assertThat(images(pdf)).isZero();
    }

    // ---- Seguridad: imágenes de Markdown ----

    @Test
    void unaImagenMarkdownRemotaNoSeDescargaYSeMuestraSuTextoAlternativo() throws IOException {
        String markdown = "![Logo de la empresa](" + serverUrl("/md.png") + ")";

        assertThat(renderer.html(NAME, markdown)).doesNotContain("<img").doesNotContain(serverUrl("/md.png"));
        byte[] pdf = renderer.render(NAME, markdown);

        assertThat(requests.get()).isZero();
        assertThat(images(pdf)).isZero();
        assertThat(text(pdf)).contains("Logo de la empresa");
    }

    @Test
    void unaImagenFileNoSeLee() throws IOException {
        Path png = tempDir.resolve("local.png");
        Files.write(png, java.util.Base64.getDecoder().decode(PNG_BASE64));
        String markdown = "![passwd](file:///etc/passwd)\n\n![local](" + png.toUri() + ")";

        assertThat(renderer.html(NAME, markdown)).doesNotContain("<img").doesNotContain("file:");
        byte[] pdf = renderer.render(NAME, markdown);

        assertThat(images(pdf)).isZero();
        assertThat(text(pdf)).doesNotContain("root:");
    }

    @Test
    void unaImagenDataNoSeIncrusta() throws IOException {
        byte[] pdf = renderer.render(NAME, "![pixel](data:image/png;base64," + PNG_BASE64 + ")");

        assertThat(images(pdf)).isZero();
    }

    // ---- Seguridad: enlaces ----

    @Test
    void unEnlaceJavascriptSeQuitaPeroSuTextoSeMantiene() throws IOException {
        String markdown = "[pulsa aquí](javascript:alert(1)) y [también](JaVaScRiPt:alert(2))";

        assertThat(renderer.html(NAME, markdown).toLowerCase()).doesNotContain("javascript");
        byte[] pdf = renderer.render(NAME, markdown);

        assertThat(linkUris(pdf)).noneMatch(uri -> uri.toLowerCase().contains("javascript"));
        assertThat(text(pdf)).contains("pulsa aquí").contains("también");
    }

    @Test
    void losEnlacesHttpsSeMantienenComoEnlacesSinDescargarse() throws IOException {
        byte[] pdf = renderer.render(NAME, "Mi [repositorio](" + serverUrl("/repo") + ")");

        assertThat(linkUris(pdf)).containsExactly(serverUrl("/repo"));
        assertThat(requests.get()).isZero();
    }
}
