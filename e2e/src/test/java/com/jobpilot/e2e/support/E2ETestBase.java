package com.jobpilot.e2e.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base de los tests E2E: un Chromium por clase y un contexto (sesión limpia: sin {@code localStorage}) por test,
 * con una ventana de escritorio (el tablero en columnas necesita 768 px o más).
 *
 * <p>Cada test graba una traza de Playwright. Si falla, {@link FailureArtifacts} la guarda junto con una captura en
 * {@code target/e2e-artifacts/}; se abre con {@code npx playwright show-trace <zip>} o en trace.playwright.dev.
 *
 * <p>Sin esperas fijas: solo el auto-wait de Playwright, {@code assertThat(...)} y {@code waitForResponse}.
 */
@ExtendWith(FailureArtifacts.class)
public abstract class E2ETestBase {

    public static final String FRONTEND_URL = setting("frontendUrl", "FRONTEND_URL", "http://localhost:5173")
            .replaceAll("/+$", "");

    /** Por defecto, sin ventana. {@code -Dheadless=false} (o {@code E2E_HEADLESS=false}) abre el navegador. */
    static final boolean HEADLESS = Boolean.parseBoolean(setting("headless", "E2E_HEADLESS", "true"));

    /** Pausa en ms tras cada acción de Playwright ({@code -DslowMo=600} o {@code E2E_SLOW_MO}); por defecto, 0. */
    static final double SLOW_MO = Double.parseDouble(setting("slowMo", "E2E_SLOW_MO", "0"));

    /**
     * Espera máxima de las acciones, aserciones y {@code waitForResponse}: 15 s de margen para el stack en Docker, más
     * un margen proporcional a {@link #SLOW_MO} para que ir despacio nunca haga fallar un test.
     */
    static final double TIMEOUT_MS = 15_000 + 60 * SLOW_MO;
    static final Path ARTIFACTS = Paths.get("target", "e2e-artifacts");

    private static Playwright playwright;
    private static Browser browser;

    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    static void launchBrowser() {
        PlaywrightAssertions.setDefaultAssertionTimeout(TIMEOUT_MS);
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS).setSlowMo(SLOW_MO));
    }

    @AfterAll
    static void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openContext(TestInfo testInfo) {
        context = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(FRONTEND_URL)
                .setViewportSize(1440, 900)
                .setLocale("es-ES")
                .setTimezoneId("Europe/Madrid")
                .setAcceptDownloads(true));
        context.setDefaultTimeout(TIMEOUT_MS);
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true)
                .setTitle(testInfo.getDisplayName()));
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        // FailureArtifacts (afterTestExecution) ya ha guardado la traza si el test ha fallado.
        context.close();
    }

    /** Primero la propiedad de sistema ({@code -Dnombre=...} en Maven), después la variable de entorno. */
    private static String setting(String property, String envVar, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
