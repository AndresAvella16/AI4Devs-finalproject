package com.jobpilot.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String PROBLEMS = "https://jobpilot.dev/problems/";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundException_devuelve404ComoProblemDetail() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "not-found"))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("La oferta no existe."))
                .andExpect(jsonPath("$.instance").value("/test/not-found"));
    }

    @Test
    void conflictException_usaElTipoYElTituloDelCaso() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-status-transition"))
                .andExpect(jsonPath("$.title").value("Transición de estado no permitida"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("No se puede pasar de OFERTA a ENVIADA."))
                .andExpect(jsonPath("$.instance").value("/test/conflict"));
    }

    @Test
    void dtoNoValido_devuelve400ConLaListaDeErroresPorCampo() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"no-es-un-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-error"))
                .andExpect(jsonPath("$.title").value("Datos de entrada no válidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/test/validated"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[1].field").value("name"))
                .andExpect(jsonPath("$.errors[1].message").isNotEmpty());
    }

    @Test
    void jsonMalFormado_devuelve400SinDetallesInternos() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "malformed-request"))
                .andExpect(jsonPath("$.title").value("Petición mal formada"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/test/validated"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(not(containsString("jackson"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void llmTimeoutException_devuelve504() throws Exception {
        mockMvc.perform(get("/test/llm-timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "llm-timeout"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/llm-timeout"))
                .andExpect(content().string(not(containsString("interno"))));
    }

    @Test
    void llmInvalidResponseException_devuelve502() throws Exception {
        mockMvc.perform(get("/test/llm-invalid"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "llm-invalid-response"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/llm-invalid"))
                .andExpect(content().string(not(containsString("interno"))));
    }

    @Test
    void llmOverloadedException_devuelve503ConRetryAfter() throws Exception {
        mockMvc.perform(get("/test/llm-overloaded"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "llm-overloaded"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/llm-overloaded"));
    }

    @Test
    void excepcionNoControlada_devuelve500GenericoYLaRegistraEnElLog(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "internal-error"))
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.instance").value("/test/boom"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("secreto"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));

        assertThat(output.getOut())
                .contains("ERROR")
                .contains("java.lang.IllegalStateException: secreto interno")
                .contains("at com.jobpilot.shared.error.GlobalExceptionHandlerTest$ErrorTestController.boom");
    }

    @Test
    void metodoNoPermitido_devuelve405ComoProblemDetail() throws Exception {
        mockMvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.instance").value("/test/not-found"));
    }

    @Test
    void tipoDeContenidoNoSoportado_devuelve415ComoProblemDetail() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.instance").value("/test/validated"));
    }

    @Test
    void llmOverloadedException_rechazaUnRetryAfterNoPositivo() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new LlmOverloadedException(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Controller solo de test. Al estar anidado en una clase de test, el component scan de los IT lo ignora. */
    @RestController
    static class ErrorTestController {

        record Payload(@NotBlank String name, @Email String email) {
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw new NotFoundException("La oferta no existe.");
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw new ConflictException("invalid-status-transition", "Transición de estado no permitida",
                    "No se puede pasar de OFERTA a ENVIADA.");
        }

        @PostMapping("/test/validated")
        String validated(@Valid @RequestBody Payload payload) {
            return "ok";
        }

        @GetMapping("/test/llm-timeout")
        String llmTimeout() {
            throw new LlmTimeoutException("detalle interno del proveedor");
        }

        @GetMapping("/test/llm-invalid")
        String llmInvalid() {
            throw new LlmInvalidResponseException("detalle interno: JSON roto");
        }

        @GetMapping("/test/llm-overloaded")
        String llmOverloaded() {
            throw new LlmOverloadedException(30);
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("secreto interno: password=xyz");
        }
    }
}
