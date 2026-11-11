package com.jobpilot.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobpilot.e2e.support.E2EData;
import com.jobpilot.e2e.support.E2ETestBase;
import com.jobpilot.e2e.support.SampleCv;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.FilePayload;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JP-QA-01 / JP-FE-02 / JP-FE-05 · Flujo principal (readme 2.6): registro → subir el CV de ejemplo → revisar y
 * guardar el perfil → pegar una oferta → requisitos → análisis (85) → generar la carta → editarla y guardarla
 * (versión 2) → exportar el PDF y verificar la descarga → guardar en el tablero → arrastrar a "Enviada" → recargar.
 *
 * <p>Cada ejecución crea su propia cuenta (email aleatorio), importa su propia oferta y localiza su tarjeta por
 * {@code data-offer-id}: la suite se puede repetir sobre el mismo stack sin reiniciarlo.
 */
class MainFlowE2ETest extends E2ETestBase {

    private static final Pattern OFFER_URL = Pattern.compile("/ofertas/([0-9a-f-]{36})$");

    @Test
    @DisplayName("Flujo principal: registro → CV → perfil → oferta → análisis → carta → PDF → tablero → Enviada, y se mantiene al recargar")
    void flujoPrincipalDelRegistroAlTablero() {
        register();
        uploadCvAndSaveProfile();

        String offerId = importOffer();
        checkRequirements();
        checkAnalysis();

        generateEditAndExportLetter();

        saveToBoard();
        Locator card = card(offerId);
        assertThat(column("GUARDADA").locator(card)).isVisible();

        dragToColumn(card, "ENVIADA");
        assertThat(column("ENVIADA").locator(card)).isVisible();

        page.reload();
        assertThat(column("ENVIADA").locator(card)).isVisible();
        assertThat(column("GUARDADA").locator(card)).hasCount(0);
    }

    private static final String FULL_NAME = "Candidata Ficticia E2E";

    /** Cuenta nueva: el registro lleva al onboarding del perfil. */
    private void register() {
        page.navigate("/registro");
        page.getByTestId("register-fullname").fill(FULL_NAME);
        page.getByTestId("register-email").fill("flujo-" + UUID.randomUUID() + "@jobpilot.test");
        page.getByTestId("register-password").fill(E2EData.REGISTER_PASSWORD);
        page.getByTestId("register-submit").click();

        assertThat(page).hasURL(FRONTEND_URL + "/perfil");
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Crea tu perfil").setLevel(1)))
                .isVisible();
    }

    /**
     * Sube el CV ficticio, revisa el borrador de la IA (fake), cambia la ubicación y guarda. El primer guardado
     * (201) lleva al dashboard, que ya no pide crear el perfil.
     */
    private void uploadCvAndSaveProfile() {
        page.getByTestId("cv-file-input")
                .setInputFiles(new FilePayload(SampleCv.FILE_NAME, "application/pdf", SampleCv.pdf()));

        assertThat(page.getByTestId("cv-draft-notice")).containsText(SampleCv.FILE_NAME);
        assertThat(page.getByTestId("profile-headline")).hasValue(E2EData.FAKE_CV_HEADLINE);
        assertThat(page.getByTestId("skill-chip")).hasCount(E2EData.FAKE_CV_SKILLS);
        page.getByTestId("profile-location").fill("Valencia (híbrido)");

        Response response = page.waitForResponse(
                r -> r.request().method().equals("PUT") && r.url().endsWith("/api/v1/profile"),
                () -> page.getByTestId("profile-save-button").click());
        assertEquals(201, response.status(), "PUT /profile → " + response.status());

        assertThat(page.getByTestId("home-greeting")).hasText("Hola, " + FULL_NAME);
        assertThat(page.getByTestId("profile-missing-cta")).hasCount(0);
    }

    private String importOffer() {
        page.getByTestId("new-offer-button").click();
        page.getByTestId("offer-raw-text").fill(E2EData.OFFER_TEXT);
        page.getByTestId("offer-submit").click();

        assertThat(page).hasURL(OFFER_URL);
        Matcher matcher = OFFER_URL.matcher(page.url());
        assertTrue(matcher.find(), "la URL del detalle lleva el id de la oferta: " + page.url());
        return matcher.group(1);
    }

    private void checkRequirements() {
        assertThat(page.getByTestId("offer-title")).hasText(E2EData.FAKE_OFFER_TITLE);
        assertThat(page.getByTestId("offer-requirements-mandatory").locator("li")).hasCount(3);
        assertThat(page.getByTestId("offer-requirements-optional").locator("li")).hasCount(3);
    }

    private void checkAnalysis() {
        page.getByTestId("calculate-analysis-button").click();
        assertThat(page.getByTestId("analysis-score"))
                .hasAttribute("aria-label", "Encaje: " + E2EData.FAKE_SCORE + " de 100");
        assertThat(page.getByTestId("requirement-match-row")).hasCount(6);
    }

    /**
     * Genera la carta (LLM fake: fake-llm/document-carta.json, firmada por el servidor con el nombre de la cuenta),
     * añade una línea y la guarda como versión 2 ("Editada por mí"), y exporta el PDF. Mientras hay cambios sin guardar,
     * "Exportar PDF" está desactivado. Del PDF se comprueba el nombre, que no esté vacío y la firma {@code %PDF-}; su
     * contenido y la cabecera con el nombre ya los prueban {@code PdfRendererTest} y {@code DocumentControllerIT}.
     */
    private void generateEditAndExportLetter() {
        Locator letter = page.getByTestId("document-panel-CARTA");
        letter.getByTestId("document-generate-button").click();

        Locator editor = letter.getByTestId("document-editor");
        // Playwright pasa el patrón a una RegExp de JS: nada de Pattern.quote (\Q...\E) ni de flags en línea. El título
        // y el nombre no tienen metacaracteres de regex.
        assertThat(editor).hasValue(Pattern.compile("puesto de " + E2EData.FAKE_OFFER_TITLE));
        assertThat(editor).hasValue(Pattern.compile("Atentamente,\\n\\n" + FULL_NAME + "$"));
        assertThat(letter.getByTestId("document-version")).hasText("Versión 1 · Generada por la IA");

        Locator export = letter.getByTestId("document-export-button");
        editor.fill(editor.inputValue() + "\n\nP. D.: Tengo disponibilidad inmediata.");
        assertThat(export).isDisabled();

        Response saved = page.waitForResponse(
                r -> r.request().method().equals("POST") && r.url().matches(".*/api/v1/documents/[0-9a-f-]{36}/versions$"),
                () -> letter.getByTestId("document-save-button").click());
        assertEquals(201, saved.status(), "POST /documents/{id}/versions → " + saved.status());
        assertThat(letter.getByTestId("document-version")).hasText("Versión 2 · Editada por mí");
        assertThat(export).isEnabled();

        Download download = page.waitForDownload(export::click);
        assertEquals("carta-v2.pdf", download.suggestedFilename());
        byte[] pdf = readAll(download);
        assertTrue(pdf.length > 5, "el PDF no está vacío");
        assertEquals("%PDF-", new String(Arrays.copyOf(pdf, 5), StandardCharsets.US_ASCII));
    }

    private static byte[] readAll(Download download) {
        try {
            return Files.readAllBytes(download.path());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void saveToBoard() {
        page.getByTestId("save-to-board-button").click();
        page.getByTestId("view-on-board-link").click();
        assertThat(page.getByTestId("kanban-board")).isVisible();
    }

    private Locator column(String status) {
        return page.getByTestId("kanban-column-" + status);
    }

    private Locator card(String offerId) {
        return page.locator("[data-testid='application-card'][data-offer-id='" + offerId + "']");
    }

    /**
     * Arrastre con el ratón para el {@code PointerSensor} de dnd-kit: {@code dragTo()} no sirve porque dnd-kit solo
     * empieza a arrastrar después de mover el puntero 5 px con el botón pulsado y necesita varios {@code pointermove}.
     * Se coge la tarjeta por su asa (⠿) para no hacer clic en el título, que abre el panel lateral. Antes de seguir se
     * espera la respuesta del {@code PATCH /applications/{id}/status}.
     */
    private void dragToColumn(Locator card, String status) {
        Locator handle = card.locator("button[aria-label^='Mover la candidatura']");
        handle.scrollIntoViewIfNeeded();
        BoundingBox from = handle.boundingBox();
        BoundingBox to = column(status).boundingBox();
        double startX = from.x + from.width / 2;
        double startY = from.y + from.height / 2;
        double endX = to.x + to.width / 2;
        double endY = to.y + Math.min(to.height / 2, 120);

        Response response = page.waitForResponse(
                r -> r.request().method().equals("PATCH") && r.url().matches(".*/api/v1/applications/[0-9a-f-]{36}/status$"),
                () -> {
                    page.mouse().move(startX, startY);
                    page.mouse().down();
                    page.mouse().move(startX + 10, startY + 10, new Mouse.MoveOptions().setSteps(5));
                    page.mouse().move(endX, endY, new Mouse.MoveOptions().setSteps(20));
                    page.mouse().up();
                });
        assertEquals(200, response.status(), "PATCH /status → " + response.status());
    }
}
