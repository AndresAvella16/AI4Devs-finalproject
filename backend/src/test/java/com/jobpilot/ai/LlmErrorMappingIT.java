package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.InternalServerException;
import com.jobpilot.auth.service.TokenService;
import com.jobpilot.support.PostgresTestcontainer;
import jakarta.validation.Validator;
import java.io.InterruptedIOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * CA 8: los errores del LLM llegan al cliente como {@code ProblemDetail} (504 / 503 + Retry-After / 502), pasando por
 * los beans reales de limitación de concurrencia, traducción de errores y plantillas. Solo el {@link ChatModel} es
 * simulado ({@link ScriptedChatModel}): el contexto arranca en modo fake y nunca se llama a Anthropic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, LlmErrorMappingIT.TestLlmConfig.class, LlmErrorMappingIT.EchoController.class})
class LlmErrorMappingIT {

    private static final String ENDPOINT = "/api/v1/test/llm-echo";
    private static final String VALID = "{\"answer\": \"ok\", \"items\": [\"java\"]}";

    static final ScriptedChatModel CHAT_MODEL = new ScriptedChatModel();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokens;

    @Autowired
    private StructuredLlmCall call;

    private final ExecutorService threads = Executors.newCachedThreadPool();

    @AfterEach
    void cleanUp() throws InterruptedException {
        // Espera a que acaben las llamadas en segundo plano: si no, seguirían ocupando huecos del semáforo real.
        threads.shutdown();
        assertThat(threads.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        CHAT_MODEL.reset();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestLlmConfig {

        /** Los mismos beans que usa el modo real, salvo el modelo. */
        @Bean
        StructuredLlmCall structuredLlmCall(PromptTemplates templates, LlmConcurrencyLimiter limiter,
                ProviderErrorTranslator translator, Validator validator) {
            return new StructuredLlmCall(CHAT_MODEL, templates, limiter, translator, validator);
        }
    }

    /** Endpoint protegido que solo existe en este test. */
    @RestController
    static class EchoController {

        private final StructuredLlmCall call;

        EchoController(StructuredLlmCall call) {
            this.call = call;
        }

        @PostMapping(ENDPOINT)
        EchoResult echo(@RequestBody String text) {
            return call.execute(echoRequest(text));
        }
    }

    private static StructuredRequest<EchoResult> echoRequest(String text) {
        return StructuredRequest.builder("test-echo", EchoResult.class)
                .variable("tarea", "resumir")
                .data("texto", text)
                .build();
    }

    private ResultActions postEcho() throws Exception {
        return mockMvc.perform(post(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(UUID.randomUUID()).value())
                .contentType(MediaType.TEXT_PLAIN)
                .content("Texto del usuario"));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String slug) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://jobpilot.dev/problems/" + slug))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.instance").value(ENDPOINT));
    }

    @Test
    void respuestaValidaDevuelve200() throws Exception {
        CHAT_MODEL.respond(VALID);

        postEcho().andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("ok"));
    }

    @Test
    void timeoutDelLlmDevuelve504() throws Exception {
        CHAT_MODEL.fail(new AnthropicIoException("Request failed", new InterruptedIOException("timeout")));

        expectProblem(postEcho(), 504, "llm-timeout")
                .andExpect(jsonPath("$.title").value("La IA no ha respondido a tiempo"));
    }

    @Test
    void respuestaNoValidaDosVecesDevuelve502() throws Exception {
        CHAT_MODEL.respond("esto no es JSON");

        expectProblem(postEcho(), 502, "llm-invalid-response")
                .andExpect(jsonPath("$.detail").value(not(containsString("esto no es JSON"))));
        assertThat(CHAT_MODEL.calls()).isEqualTo(2);
    }

    @Test
    void proveedorSaturadoDevuelve503ConElRetryAfterConfigurado() throws Exception {
        CHAT_MODEL.fail(InternalServerException.builder()
                .statusCode(529).headers(Headers.builder().build()).body(JsonValue.from(Map.of())).build());

        expectProblem(postEcho(), 503, "llm-overloaded")
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"));
    }

    @Test
    void concurrenciaAgotadaDevuelve503ConRetryAfterSinLlamarAlModelo() throws Exception {
        CountDownLatch started = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        CHAT_MODEL.answer(prompt -> {
            started.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ScriptedChatModel.text(VALID);
        });
        for (int i = 0; i < 3; i++) {
            threads.submit(() -> call.execute(echoRequest("ocupa un hueco")));
        }

        try {
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            expectProblem(postEcho(), 503, "llm-overloaded")
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "10"));
            assertThat(CHAT_MODEL.calls()).isEqualTo(3);
        } finally {
            release.countDown();
        }
    }
}
