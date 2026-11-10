package com.jobpilot.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.jobpilot.e2e.support.E2ETestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JP-QA-01 · Registro de una cuenta nueva. Cada ejecución usa un email distinto, así que la suite se puede repetir
 * sobre el mismo stack. El registro no tiene límite de peticiones (el de 2.5 es solo para el login).
 */
class RegistrationE2ETest extends E2ETestBase {

    @Test
    @DisplayName("Registro: una cuenta nueva entra en la app y ve su nombre")
    void unaCuentaNuevaEntraEnLaAppYVeSuNombre() {
        String fullName = "Registro E2E";
        String email = "registro-" + UUID.randomUUID() + "@jobpilot.test";

        page.navigate("/registro");
        page.getByTestId("register-fullname").fill(fullName);
        page.getByTestId("register-email").fill(email);
        page.getByTestId("register-password").fill("Registro1234");
        page.getByTestId("register-submit").click();

        assertThat(page).hasURL(FRONTEND_URL + "/");
        assertThat(page.getByTestId("current-user-name")).hasText(fullName);
        assertThat(page.getByTestId("home-greeting")).hasText("Hola, " + fullName);
        // Cuenta recién creada: sin ofertas.
        assertThat(page.getByTestId("offers-empty")).isVisible();
    }
}
