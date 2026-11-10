package com.jobpilot.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobpilot.e2e.support.E2EData;
import com.jobpilot.e2e.support.E2ETestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.BoundingBox;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JP-QA-01 · Flujo principal (readme 2.6), con lo que está implementado hoy:
 * login → pegar una oferta → requisitos → análisis (85) → guardar en el tablero → arrastrar a "Enviada" → recargar.
 *
 * <p>Huecos pendientes, marcados con {@code TODO} donde irán:
 * <ul>
 *   <li>registro → subir el CV de ejemplo → revisar el perfil (JP-BE-04 / JP-FE-02). Mientras tanto se usa el usuario
 *       del seed de E2E, que ya tiene perfil; el registro se prueba aparte en {@link RegistrationE2ETest}.</li>
 *   <li>generar la carta → exportar el PDF y verificar la descarga (JP-BE-07 / JP-FE-05).</li>
 * </ul>
 *
 * <p>Cada ejecución importa su propia oferta y localiza su tarjeta por {@code data-offer-id}: la suite se puede
 * repetir sobre el mismo stack sin reiniciarlo.
 */
class MainFlowE2ETest extends E2ETestBase {

    private static final Pattern OFFER_URL = Pattern.compile("/ofertas/([0-9a-f-]{36})$");

    @Test
    @DisplayName("Flujo principal: oferta → análisis → tablero → Enviada, y se mantiene al recargar")
    void flujoPrincipalDeLaOfertaAlTablero() {
        // TODO(JP-BE-04 / JP-FE-02): registro → subir el CV de ejemplo → revisar el perfil, en lugar del login.
        login();

        String offerId = importOffer();
        checkRequirements();
        checkAnalysis();

        // TODO(JP-BE-07 / JP-FE-05): generar la carta → exportar el PDF (page.waitForDownload) y comprobar el fichero.

        saveToBoard();
        Locator card = card(offerId);
        assertThat(column("GUARDADA").locator(card)).isVisible();

        dragToColumn(card, "ENVIADA");
        assertThat(column("ENVIADA").locator(card)).isVisible();

        page.reload();
        assertThat(column("ENVIADA").locator(card)).isVisible();
        assertThat(column("GUARDADA").locator(card)).hasCount(0);
    }

    private void login() {
        page.navigate("/login");
        page.getByTestId("login-email").fill(E2EData.SEED_EMAIL);
        page.getByTestId("login-password").fill(E2EData.SEED_PASSWORD);
        page.getByTestId("login-submit").click();
        assertThat(page.getByTestId("home-greeting")).hasText("Hola, " + E2EData.SEED_FULL_NAME);
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
