package com.jobpilot.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.shared.error.CvWithoutTextException;
import com.jobpilot.shared.error.InvalidCvFileException;
import com.jobpilot.shared.error.InvalidRequestException;
import com.jobpilot.support.TestPdfs;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Extracción del texto del CV con PDFBox (sección 2.6, {@code CvTextExtractorTest}): PDFs con texto, ficheros que
 * no son PDF, PDFs corruptos o cifrados, escaneados sin texto y los límites de tamaño, páginas y longitud.
 */
class CvTextExtractorTest {

    private static final String PDF = "application/pdf";

    private final CvTextExtractor extractor = new CvTextExtractor();

    private static void assertFileFieldError(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("file"));
    }

    // ---- PDF con texto ----

    @Test
    void extraeElTextoDeUnPdf() {
        String text = extractor.extract(PDF, TestPdfs.sampleCv());

        assertThat(text).contains("Desarrolladora Web Junior").contains("Spring Boot").contains("Inglés B2");
    }

    @Test
    void extraeElTextoDeTodasLasPaginasEnOrden() {
        String text = extractor.extract(PDF, TestPdfs.withText(
                "Experiencia: Tecnologías Turia, desarrolladora Java desde 2025.",
                "Formación: CFGS Desarrollo de Aplicaciones Web, IES Ficticio."));

        assertThat(text).containsSubsequence("Tecnologías Turia", "CFGS Desarrollo de Aplicaciones Web");
    }

    @Test
    void elTextoSeDevuelveSinEspaciosAlPrincipioNiAlFinal() {
        String text = extractor.extract(PDF, TestPdfs.sampleCv());

        assertThat(text).isEqualTo(text.strip());
    }

    @Test
    void elTipoMimeNoDistingueMayusculasYAdmiteParametros() {
        assertThat(extractor.extract("Application/PDF", TestPdfs.sampleCv())).contains("Spring Boot");
        assertThat(extractor.extract("application/pdf; name=cv.pdf", TestPdfs.sampleCv())).contains("Spring Boot");
    }

    @Test
    void lasHabilidadesAgrupadasYLasNotasEntreParentesisSeConservanTalCual() {
        String text = extractor.extract(PDF, TestPdfs.withText(TestPdfs.SAMPLE_CV_TEXT,
                "Habilidades: PHP / Java (según tu caso), Docker y Kubernetes"));

        assertThat(text).contains("PHP / Java (según tu caso), Docker y Kubernetes");
    }

    // ---- Tamaño ----

    @Test
    void unFicheroVacioEsUnErrorDeValidacion() {
        assertFileFieldError(() -> extractor.extract(PDF, new byte[0]));
        assertFileFieldError(() -> extractor.extract(PDF, null));
    }

    @Test
    void unFicheroDeMasDe5MbEsUnErrorDeValidacionSinLeerlo() {
        byte[] big = new byte[CvTextExtractor.MAX_FILE_BYTES + 1];
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, big, 0, header.length);

        assertFileFieldError(() -> extractor.extract(PDF, big));
    }

    @Test
    void elLimiteEsDe5MbExactos() {
        assertThat(CvTextExtractor.MAX_FILE_BYTES).isEqualTo(5 * 1024 * 1024);
    }

    // ---- No es un PDF ----

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"text/plain", "image/png", "application/octet-stream", "application/pdfx"})
    void unTipoMimeQueNoEsPdfSeRechaza(String contentType) {
        assertThatThrownBy(() -> extractor.extract(contentType, TestPdfs.sampleCv()))
                .isInstanceOf(InvalidCvFileException.class);
    }

    @Test
    void unFicheroSinLaFirmaPdfSeRechazaAunqueDigaQueEsPdf() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0};

        assertThatThrownBy(() -> extractor.extract(PDF, png)).isInstanceOf(InvalidCvFileException.class);
        assertThatThrownBy(() -> extractor.extract(PDF, "Hola, soy un CV en texto".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidCvFileException.class);
    }

    @Test
    void unPdfCorruptoSeRechaza() {
        byte[] corrupt = "%PDF-1.7\nesto no es un PDF de verdad".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> extractor.extract(PDF, corrupt)).isInstanceOf(InvalidCvFileException.class);
    }

    @Test
    void unPdfTruncadoSeRechaza() {
        byte[] cv = TestPdfs.sampleCv();

        assertThatThrownBy(() -> extractor.extract(PDF, Arrays.copyOf(cv, 40)))
                .isInstanceOf(InvalidCvFileException.class);
    }

    @Test
    void unPdfConContrasenaDeAperturaSeRechaza() {
        assertThatThrownBy(() -> extractor.extract(PDF, TestPdfs.encrypted()))
                .isInstanceOf(InvalidCvFileException.class);
    }

    // ---- Páginas ----

    @Test
    void admiteHasta10Paginas() {
        assertThat(extractor.extract(PDF, TestPdfs.pages(10))).contains("Página 10");
    }

    @Test
    void masDe10PaginasEsUnErrorDeValidacion() {
        assertFileFieldError(() -> extractor.extract(PDF, TestPdfs.pages(11)));
    }

    // ---- Sin texto (escaneado) ----

    @Test
    void unPdfSinTextoEsUnCvSinTexto() {
        assertThatThrownBy(() -> extractor.extract(PDF, TestPdfs.blankPages(2)))
                .isInstanceOf(CvWithoutTextException.class);
    }

    @Test
    void unPdfConMenosDe50CaracteresUtilesEsUnCvSinTexto() {
        // 49 caracteres que no son espacios, repartidos entre espacios.
        String text = "abcdefg ".repeat(7);
        assertThat(text.replaceAll("\\s", "")).hasSize(49);

        assertThatThrownBy(() -> extractor.extract(PDF, TestPdfs.withText(text)))
                .isInstanceOf(CvWithoutTextException.class);
    }

    @Test
    void con50CaracteresUtilesYaHayTexto() {
        String text = "abcdefg ".repeat(7) + "h";

        assertThat(extractor.extract(PDF, TestPdfs.withText(text)).replaceAll("\\s", "")).hasSize(50);
    }

    // ---- Longitud del texto ----

    @Test
    void unTextoMasLargoQueElMaximoEsUnErrorDeValidacionSinTruncarlo() {
        CvTextExtractor smallLimit = new CvTextExtractor(CvTextExtractor.MAX_FILE_BYTES, CvTextExtractor.MAX_PAGES,
                CvTextExtractor.MIN_TEXT_CHARS, 100);

        assertFileFieldError(() -> smallLimit.extract(PDF, TestPdfs.pages(2)));
    }

    @Test
    void elTextoMaximoPorDefectoEsDe50000Caracteres() {
        assertThat(CvTextExtractor.MAX_TEXT_CHARS).isEqualTo(50_000);
    }

    // ---- Limpieza ----

    @Test
    void elTextoNoLlevaCaracteresDeControlQuePostgresqlNoAdmite() {
        String text = extractor.extract(PDF, TestPdfs.sampleCv());

        assertThat(text).doesNotContain("\u0000");
        assertThat(text.chars().filter(c -> c < 0x20 && c != '\n' && c != '\t')).isEmpty();
    }

    @Test
    void sanearQuitaLosCaracteresDeControlYCompactaLasLineasEnBlanco() {
        assertThat(CvTextExtractor.clean("  Java\u0000 y\r\nSpring\u0007\n\n\n\n\nBoot\t \n  "))
                .isEqualTo("Java y\nSpring\n\nBoot");
    }
}
