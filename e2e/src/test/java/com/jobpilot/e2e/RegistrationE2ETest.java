package com.jobpilot.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.jobpilot.e2e.support.E2EData;
import com.jobpilot.e2e.support.E2ETestBase;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JP-QA-01 / JP-FE-02 · Registro de una cuenta nueva: entra en la app y va al onboarding del perfil. Cada ejecución usa un email distinto, así que la suite se puede repetir
 * sobre el mismo stack. El registro no tiene límite de peticiones (el de 2.5 es solo para el login).
 */
class RegistrationE2ETest extends E2ETestBase {

    @Test
    @DisplayName("Registro: una cuenta nueva entra en la app, va a crear su perfil y el dashboard se lo recuerda")
    void unaCuentaNuevaEntraEnLaAppYVaACrearSuPerfil() {
        String fullName = "Registro E2E";
        String email = "registro-" + UUID.randomUUID() + "@jobpilot.test";

        page.navigate("/registro");
        page.getByTestId("register-fullname").fill(fullName);
        page.getByTestId("register-email").fill(email);
        page.getByTestId("register-password").fill(E2EData.REGISTER_PASSWORD);
        page.getByTestId("register-submit").click();

        // Un usuario nuevo nunca tiene perfil: el registro lleva al onboarding (sección 1.3).
        assertThat(page).hasURL(FRONTEND_URL + "/perfil");
        assertThat(page.getByTestId("current-user-name")).hasText(fullName);
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Crea tu perfil").setLevel(1)))
                .isVisible();
        assertThat(page.getByTestId("cv-dropzone")).isVisible();

        // Si sale sin crearlo, el dashboard se lo recuerda. Cuenta recién creada: sin ofertas.
        page.getByTestId("nav-offers").click();
        assertThat(page.getByTestId("home-greeting")).hasText("Hola, " + fullName);
        assertThat(page.getByTestId("profile-missing-cta")).isVisible();
        assertThat(page.getByTestId("offers-empty")).isVisible();
    }
}
