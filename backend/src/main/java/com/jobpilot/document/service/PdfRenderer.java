package com.jobpilot.document.service;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.commonmark.node.Code;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

/**
 * Exporta una carta o un CV adaptado a PDF (HU-04, "Exportación"): Markdown → XHTML con CommonMark → PDF con
 * OpenHTMLtoPDF, con el nombre del usuario en la cabecera. Se genera en memoria en cada petición y no se guarda.
 *
 * <p>El Markdown lo escribe el usuario (o la IA a partir de una oferta pegada por él), así que no es de fiar
 * (sección 2.5):
 * <ul>
 *   <li>El HTML que lleve se escapa ({@code escapeHtml}): un {@code <script>} o un {@code <img>} se ven como texto.</li>
 *   <li>Las imágenes de Markdown no se insertan: se escribe su texto alternativo.</li>
 *   <li>Solo se conservan los enlaces {@code http}, {@code https} y {@code mailto}; de los demás
 *       ({@code javascript:}, {@code file:}, {@code data:}...) se deja solo el texto.</li>
 *   <li>Además, el motor del PDF no puede cargar ningún recurso externo (http, https, file, data, jar...): se rechaza
 *       antes y después de resolver la URI, el resolvedor no resuelve nada y no hay implementación de ningún
 *       protocolo. Así un fallo en lo anterior tampoco permite SSRF ni leer ficheros del servidor.</li>
 * </ul>
 *
 * <p>Usa las fuentes estándar de PDF (Helvetica): cubren el español, el inglés y "€". Los caracteres que no cubren
 * (emojis, alfabetos no latinos) salen como "#" (deuda técnica: incrustar una fuente TTF libre).
 */
@Component
class PdfRenderer {

    private static final Pattern SAFE_LINK = Pattern.compile("^(https?://|mailto:).+", Pattern.CASE_INSENSITIVE);

    /** Protocolos a los que el motor del PDF podría intentar acceder: ninguno tiene implementación. */
    private static final String[] BLOCKED_PROTOCOLS = {"http", "https", "file", "data", "jar", "ftp", "classpath"};

    private static final FSStreamFactory NO_STREAMS = PdfRenderer::refuse;

    private static final String CSS = """
            @page { size: A4; margin: 2cm 2.2cm; }
            body { font-family: sans-serif; font-size: 11pt; line-height: 1.45; color: #1f2937; }
            .header { font-size: 15pt; font-weight: bold; padding-bottom: 6pt; margin-bottom: 14pt;
                      border-bottom: 1pt solid #9ca3af; }
            h1 { font-size: 16pt; margin: 0 0 8pt 0; }
            h2 { font-size: 13pt; margin: 14pt 0 6pt 0; }
            h3 { font-size: 11.5pt; margin: 10pt 0 4pt 0; }
            p { margin: 0 0 8pt 0; }
            ul, ol { margin: 0 0 8pt 0; padding-left: 16pt; }
            li { margin-bottom: 2pt; }
            blockquote { margin: 0 0 8pt 0; padding-left: 8pt; border-left: 2pt solid #d1d5db; color: #4b5563; }
            code, pre { font-family: monospace; font-size: 9.5pt; }
            pre { white-space: pre-wrap; }
            a { color: #1d4ed8; }
            """;

    private final Parser parser = Parser.builder().build();

    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .nodeRendererFactory(ImageAsAltText::new)
            .nodeRendererFactory(SafeLink::new)
            .build();

    /** El PDF del documento con {@code fullName} en la cabecera. */
    byte[] render(String fullName, String markdown) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html(fullName, markdown), null);
        builder.useExternalResourceAccessControl((uri, type) -> false,
                ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
        builder.useExternalResourceAccessControl((uri, type) -> false,
                ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
        builder.useUriResolver((baseUri, uri) -> null);
        builder.useHttpStreamImplementation(NO_STREAMS);
        builder.useProtocolsStreamImplementation(NO_STREAMS, BLOCKED_PROTOCOLS);
        builder.toStream(out);
        try {
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido generar el PDF", e);
        }
        return out.toByteArray();
    }

    /** El XHTML que se convierte en PDF (visible para los tests). */
    String html(String fullName, String markdown) {
        return "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\" /><style>" + CSS + "</style></head><body>"
                + "<div class=\"header\">" + escape(fullName) + "</div>"
                + htmlRenderer.render(parser.parse(markdown))
                + "</body></html>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static FSStream refuse(String uri) {
        throw new IllegalStateException("El PDF no carga recursos externos");
    }

    /** Las imágenes se sustituyen por su texto alternativo: nunca se genera un {@code <img>}. */
    private static final class ImageAsAltText implements NodeRenderer {

        private final HtmlNodeRendererContext context;

        ImageAsAltText(HtmlNodeRendererContext context) {
            this.context = context;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Image.class);
        }

        @Override
        public void render(Node node) {
            StringBuilder alt = new StringBuilder();
            collectText(node, alt);
            context.getWriter().text(alt.toString());
        }

        private static void collectText(Node node, StringBuilder alt) {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Text text) {
                    alt.append(text.getLiteral());
                } else if (child instanceof Code code) {
                    alt.append(code.getLiteral());
                } else {
                    collectText(child, alt);
                }
            }
        }
    }

    /** Un enlace solo se conserva si es http, https o mailto; si no, se escribe solo su texto. */
    private static final class SafeLink implements NodeRenderer {

        private final HtmlNodeRendererContext context;

        SafeLink(HtmlNodeRendererContext context) {
            this.context = context;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Link.class);
        }

        @Override
        public void render(Node node) {
            Link link = (Link) node;
            String destination = link.getDestination() == null ? "" : link.getDestination().strip();
            boolean safe = SAFE_LINK.matcher(destination).matches()
                    && !destination.toLowerCase(Locale.ROOT).contains("javascript:");
            if (safe) {
                Map<String, String> attributes = new LinkedHashMap<>();
                attributes.put("href", context.encodeUrl(destination));
                context.getWriter().tag("a", attributes);
            }
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                context.render(child);
            }
            if (safe) {
                context.getWriter().tag("/a");
            }
        }
    }
}
