package com.jobpilot.e2e.support;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Si un test falla, guarda la traza de Playwright y una captura de página completa en {@code target/e2e-artifacts/}
 * (la CI las sube como artefacto). Si pasa, descarta la traza. Se ejecuta antes del {@code @AfterEach} que cierra
 * el contexto.
 */
final class FailureArtifacts implements AfterTestExecutionCallback {

    @Override
    public void afterTestExecution(ExtensionContext extension) {
        E2ETestBase test = (E2ETestBase) extension.getRequiredTestInstance();
        if (test.context == null) {
            return;
        }
        if (extension.getExecutionException().isEmpty()) {
            test.context.tracing().stop();
            return;
        }
        String name = extension.getRequiredTestClass().getSimpleName() + "-" + extension.getRequiredTestMethod().getName();
        test.context.tracing().stop(new Tracing.StopOptions().setPath(E2ETestBase.ARTIFACTS.resolve(name + "-trace.zip")));
        if (test.page != null && !test.page.isClosed()) {
            test.page.screenshot(new Page.ScreenshotOptions()
                    .setPath(E2ETestBase.ARTIFACTS.resolve(name + ".png"))
                    .setFullPage(true));
        }
    }
}
