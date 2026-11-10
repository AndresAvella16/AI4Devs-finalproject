package com.jobpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.models.messages.MessageCreateParams;
import jakarta.validation.Validation;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Las opciones por llamada llegan de verdad a la petición HTTP de Anthropic, y nunca se envía {@code temperature}:
 * {@code claude-sonnet-5-5} la rechaza con un 400 ("`temperature` is deprecated for this model", prueba real de
 * JP-BE-04). {@code AnthropicChatModel} (Spring AI
 * 2.0) no combina las opciones del prompt con las suyas: unas {@link ChatOptions} genéricas las ignora y unas
 * {@link AnthropicChatOptions} parciales pierden el modelo. Por eso {@link StructuredLlmCall} parte de las opciones
 * del modelo y solo cambia las de la llamada. Este test construye la petición con el {@code AnthropicChatModel} real
 * (su método {@code createRequest}, sin red) a partir del prompt que genera {@link StructuredLlmCall}.
 */
class AnthropicRequestOptionsTest {

    private static final String VALID_CV = """
            {"headline": null, "summary": null, "yearsExperience": null, "location": null,
             "skills": [], "experiences": [], "education": [], "languages": []}""";

    private static final String VALID_OFFER = """
            {"title": "Java Junior", "company": null, "location": null, "workMode": "REMOTO", "salary": null,
             "requirements": [{"description": "Java", "type": "OBLIGATORIO", "category": "TECNICO"}]}""";

    /** Las opciones que crea la autoconfiguración a partir de {@code spring.ai.anthropic.*} (application.yml). */
    private final AnthropicChatModel anthropic = AnthropicChatModel.builder()
            .options(AnthropicChatOptions.builder()
                    .apiKey("clave-ficticia-solo-para-construir-la-peticion")
                    .model("claude-sonnet-5-5")
                    .maxTokens(4096)
                    .build())
            .build();

    private final AtomicReference<MessageCreateParams> sent = new AtomicReference<>();

    /** Construye la petición real de Anthropic y responde sin llamar a la API. */
    private ChatModel capturing(String answer) {
        return new ChatModel() {
            @Override
            public ChatOptions getOptions() {
                return anthropic.getOptions();
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                sent.set(createRequest(prompt));
                return ScriptedChatModel.text(answer);
            }
        };
    }

    private MessageCreateParams createRequest(Prompt prompt) {
        try {
            Method buildRequestPrompt = AnthropicChatModel.class.getDeclaredMethod("buildRequestPrompt", Prompt.class);
            buildRequestPrompt.setAccessible(true);
            Method createRequest = AnthropicChatModel.class.getDeclaredMethod("createRequest", Prompt.class,
                    boolean.class);
            createRequest.setAccessible(true);
            Prompt requestPrompt = (Prompt) buildRequestPrompt.invoke(anthropic, prompt);
            return (MessageCreateParams) createRequest.invoke(anthropic, requestPrompt, false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("La API interna de AnthropicChatModel ha cambiado", e);
        }
    }

    private SpringAiLlmGateway gateway(String answer) {
        return new SpringAiLlmGateway(new StructuredLlmCall(capturing(answer), new PromptTemplates(),
                new LlmConcurrencyLimiter(3, 10), new ProviderErrorTranslator(30),
                Validation.buildDefaultValidatorFactory().getValidator()), "claude-sonnet-5-5");
    }

    @Test
    void laExtraccionDelCvPide8192TokensConElModeloConfiguradoYSinTemperatura() {
        gateway(VALID_CV).extractProfile("Texto del CV");

        MessageCreateParams request = sent.get();
        assertThat(request.maxTokens()).isEqualTo(8192);
        assertThat(request.temperature()).isEmpty();
        assertThat(request.model().asString()).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void laExtraccionDeOfertasUsaLasOpcionesConfiguradas() {
        gateway(VALID_OFFER).extractOffer("Texto de la oferta");

        MessageCreateParams request = sent.get();
        assertThat(request.maxTokens()).isEqualTo(4096);
        assertThat(request.temperature()).isEmpty();
        assertThat(request.model().asString()).isEqualTo("claude-sonnet-5-5");
    }
}
