package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.jobpilot.shared.error.LlmOverloadedException;
import com.jobpilot.shared.error.LlmTimeoutException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** CA 5 y 6: errores del SDK de Anthropic (o envueltos por Spring AI) → excepciones propias del dominio. */
class ProviderErrorTranslatorTest {

    private static final long DEFAULT_RETRY_AFTER = 30;

    private final ProviderErrorTranslator translator = new ProviderErrorTranslator(DEFAULT_RETRY_AFTER);

    private static Headers headers(String retryAfter) {
        Headers.Builder builder = Headers.builder();
        if (retryAfter != null) {
            builder.put("retry-after", retryAfter);
        }
        return builder.build();
    }

    private static JsonValue body() {
        return JsonValue.from(Map.of("error", Map.of("message", "contenido interno del proveedor")));
    }

    private static RateLimitException rateLimit(String retryAfter) {
        return RateLimitException.builder().headers(headers(retryAfter)).body(body()).build();
    }

    private static InternalServerException serverError(int status) {
        return InternalServerException.builder().statusCode(status).headers(headers(null)).body(body()).build();
    }

    // ---- Timeout → 504 ----

    @Test
    void timeoutDeOkHttpEnvueltoPorElSdkEsLlmTimeout() {
        RuntimeException e = new AnthropicIoException("Request failed", new InterruptedIOException("timeout"));

        assertThat(translator.translate(e)).isInstanceOf(LlmTimeoutException.class).hasCause(e);
    }

    @Test
    void socketTimeoutEnvueltoEnOtraExcepcionEsLlmTimeout() {
        RuntimeException e = new IllegalStateException("envoltorio de Spring AI",
                new AnthropicIoException("Request failed", new SocketTimeoutException("Read timed out")));

        assertThat(translator.translate(e)).isInstanceOf(LlmTimeoutException.class);
    }

    // ---- Saturado o no disponible → 503 + Retry-After ----

    @Test
    void rateLimitConRetryAfterUsaLosSegundosDelProveedor() {
        assertThat(translator.translate(rateLimit("12")))
                .isInstanceOfSatisfying(LlmOverloadedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(12));
    }

    @ParameterizedTest(name = "Retry-After \"{0}\" → valor por defecto")
    @ValueSource(strings = {"", "0", "-5", "mañana", "Wed, 21 Oct 2026 07:28:00 GMT"})
    void retryAfterAusenteONoValidoUsaElValorPorDefecto(String retryAfter) {
        assertThat(translator.translate(rateLimit(retryAfter)))
                .isInstanceOfSatisfying(LlmOverloadedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(DEFAULT_RETRY_AFTER));
    }

    @Test
    void rateLimitSinCabeceraUsaElValorPorDefecto() {
        assertThat(translator.translate(rateLimit(null)))
                .isInstanceOfSatisfying(LlmOverloadedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(DEFAULT_RETRY_AFTER));
    }

    @ParameterizedTest(name = "HTTP {0} del proveedor → LlmOverloaded")
    @ValueSource(ints = {500, 502, 503, 529})
    void erroresDelServidorDelProveedorSonLlmOverloaded(int status) {
        assertThat(translator.translate(serverError(status))).isInstanceOf(LlmOverloadedException.class);
    }

    @Test
    void elProveedorNoAccesibleEsLlmOverloaded() {
        RuntimeException e = new AnthropicIoException("Request failed", new ConnectException("Connection refused"));

        assertThat(translator.translate(e)).isInstanceOf(LlmOverloadedException.class);
    }

    @Test
    void unErrorDelProveedorEnvueltoSeReconoceBuscandoLaCausa() {
        RuntimeException wrapped = new RuntimeException("envoltorio", rateLimit("7"));

        assertThat(translator.translate(wrapped))
                .isInstanceOfSatisfying(LlmOverloadedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(7));
    }

    @Test
    void elMensajeTraducidoNoIncluyeElCuerpoDeLaRespuestaDelProveedor() {
        assertThat(translator.translate(serverError(529)).getMessage())
                .doesNotContain("contenido interno del proveedor")
                .contains("529");
    }

    // ---- Errores de configuración: no se traducen (→ 500) ----

    @Test
    void unaApiKeyNoValidaNoSeTraduce() {
        RuntimeException e = UnauthorizedException.builder().headers(headers(null)).body(body()).build();

        assertThat(translator.translate(e)).isSameAs(e);
    }

    @Test
    void cualquierOtraExcepcionSeDevuelveTalCual() {
        RuntimeException e = new IllegalStateException("fallo de programación");

        assertThat(translator.translate(e)).isSameAs(e);
    }
}
