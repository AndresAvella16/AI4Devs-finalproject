package com.jobpilot.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** CA 6: con el perfil dev, las credenciales de prueba del readme (sección 1.4) permiten iniciar sesión. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(PostgresTestcontainer.class)
class DemoUserLoginIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elUsuarioDemoDelSeedPuedeIniciarSesion() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo@jobpilot.dev\",\"password\":\"Demo1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value("11111111-0000-4000-8000-000000000001"))
                .andExpect(jsonPath("$.user.fullName").value("Lucía Demo Martínez"));
    }

    @Test
    void elUsuarioDemoConOtraContrasenaNoEntra() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo@jobpilot.dev\",\"password\":\"Demo12345\"}"))
                .andExpect(status().isUnauthorized());
    }
}
