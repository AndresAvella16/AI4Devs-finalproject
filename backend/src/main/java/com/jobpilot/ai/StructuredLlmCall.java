package com.jobpilot.ai;

import com.jobpilot.shared.error.LlmInvalidResponseException;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Motor común de las llamadas al LLM con salida estructurada (sección 2.2, módulo {@code ai}):
 *
 * <ol>
 *   <li>Prompt de sistema = reglas comunes + plantilla de la tarea + esquema JSON de la respuesta.</li>
 *   <li>Mensaje de usuario = solo los datos no confiables, cada bloque entre {@code <nombre>} y {@code </nombre>}.
 *       Nunca se renderizan como plantilla, y un cierre de bloque dentro de los datos se neutraliza.</li>
 *   <li>Sin herramientas: el modelo no tiene acceso a nada. Las opciones son las del modelo
 *       ({@code spring.ai.anthropic.chat.*}), salvo {@code maxTokens} si la llamada lo cambia: se parte de una
 *       copia de las del modelo porque {@code AnthropicChatModel} no las combina por su cuenta (unas opciones
 *       parciales perderían el modelo). No se envía {@code temperature}: el modelo actual la rechaza (400).</li>
 *   <li>La respuesta se convierte al {@code record} (sin campos desconocidos) y se valida con Bean Validation y
 *       con el {@link ResponseValidator} de la tarea. Si no es válida, <b>un reintento</b>; si vuelve a fallar,
 *       {@link LlmInvalidResponseException} (502).</li>
 *   <li>Los errores del proveedor (timeout, saturación) no se reintentan: se traducen con
 *       {@link ProviderErrorTranslator}.</li>
 *   <li>Todo ello ocupa un hueco del {@link LlmConcurrencyLimiter}.</li>
 * </ol>
 *
 * <p>Ni los prompts ni las respuestas se escriben en el log: llevan datos personales del CV.
 */
class StructuredLlmCall {

    static final int MAX_ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(StructuredLlmCall.class);

    private static final JsonMapper STRICT_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final ChatModel chatModel;
    private final PromptTemplates templates;
    private final LlmConcurrencyLimiter limiter;
    private final ProviderErrorTranslator translator;
    private final Validator validator;

    StructuredLlmCall(ChatModel chatModel, PromptTemplates templates, LlmConcurrencyLimiter limiter,
            ProviderErrorTranslator translator, Validator validator) {
        this.chatModel = chatModel;
        this.templates = templates;
        this.limiter = limiter;
        this.translator = translator;
        this.validator = validator;
    }

    <T> T execute(StructuredRequest<T> request) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(request.responseType(), STRICT_MAPPER);
        Prompt prompt = buildPrompt(request, converter.getFormat());
        return limiter.run(() -> callWithOneRetry(request, prompt, converter));
    }

    private <T> T callWithOneRetry(StructuredRequest<T> request, Prompt prompt, BeanOutputConverter<T> converter) {
        List<String> problems = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long start = System.nanoTime();
            ChatResponse response = callModel(prompt);
            Parsed<T> parsed = parse(text(response), converter, request);
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (parsed.problems().isEmpty()) {
                log.debug("LLM '{}' respondió en {} ms (intento {}/{}; {})", request.promptName(), millis, attempt,
                        MAX_ATTEMPTS, usage(response));
                return parsed.value();
            }
            problems = parsed.problems();
            log.warn("Respuesta no válida del LLM para '{}' en {} ms (intento {}/{}; {}): {}", request.promptName(),
                    millis, attempt, MAX_ATTEMPTS, usage(response), problems);
        }
        throw new LlmInvalidResponseException("Respuesta no válida del LLM para '" + request.promptName()
                + "' tras " + MAX_ATTEMPTS + " intentos: " + problems);
    }

    private ChatResponse callModel(Prompt prompt) {
        try {
            return chatModel.call(prompt);
        } catch (RuntimeException e) {
            throw translator.translate(e);
        }
    }

    private static String text(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * Tokens de entrada y salida y motivo de parada, para vigilar el coste y {@code max-tokens}: una respuesta
     * cortada por el límite acaba en {@code max_tokens} y no es un JSON válido. Solo metadatos, nunca el contenido.
     */
    private static String usage(ChatResponse response) {
        if (response == null) {
            return "sin respuesta";
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        String finishReason = response.getResult() == null || response.getResult().getMetadata() == null
                ? null
                : response.getResult().getMetadata().getFinishReason();
        return "tokens entrada=" + (usage == null ? "?" : usage.getPromptTokens())
                + " salida=" + (usage == null ? "?" : usage.getCompletionTokens())
                + " parada=" + (finishReason == null ? "?" : finishReason);
    }

    private <T> Parsed<T> parse(String text, BeanOutputConverter<T> converter, StructuredRequest<T> request) {
        if (text == null || text.isBlank()) {
            return Parsed.invalid(List.of("respuesta vacía"));
        }
        T value;
        try {
            value = converter.convert(text);
        } catch (RuntimeException e) {
            // Solo el tipo: el mensaje de Jackson incluye fragmentos de la respuesta.
            return Parsed.invalid(List.of("JSON no válido para el esquema (" + e.getClass().getSimpleName() + ")"));
        }
        if (value == null) {
            return Parsed.invalid(List.of("respuesta nula"));
        }
        List<String> problems = new ArrayList<>(validator.validate(value).stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted(Comparator.naturalOrder())
                .toList());
        if (problems.isEmpty()) {
            problems.addAll(request.validator().validate(value));
        }
        return problems.isEmpty() ? Parsed.valid(value) : Parsed.invalid(problems);
    }

    private <T> Prompt buildPrompt(StructuredRequest<T> request, String jsonFormat) {
        String system = templates.systemBase()
                + "\n\n" + templates.render(request.promptName(), request.variables())
                + "\n\n" + jsonFormat;
        if (request.data().isEmpty()) {
            throw new IllegalArgumentException("La llamada '" + request.promptName() + "' no lleva datos");
        }
        String user = request.data().entrySet().stream()
                .map(StructuredLlmCall::delimit)
                .collect(Collectors.joining("\n\n"));
        List<Message> messages = List.of(new SystemMessage(system), new UserMessage(user));
        return request.hasOptions() ? new Prompt(messages, options(request)) : new Prompt(messages);
    }

    /** Las opciones del modelo con las de la llamada encima. */
    private ChatOptions options(StructuredRequest<?> request) {
        ChatOptions defaults = chatModel.getOptions();
        ChatOptions.Builder<?> builder = defaults == null ? ChatOptions.builder() : defaults.mutate();
        builder.maxTokens(request.maxTokens());
        return builder.build();
    }

    /** {@code <nombre>\n...\n</nombre>}, sin permitir que los datos cierren el bloque antes de tiempo. */
    private static String delimit(Map.Entry<String, String> section) {
        String name = section.getKey();
        Pattern closing = Pattern.compile("</\\s*" + Pattern.quote(name) + "\\s*>", Pattern.CASE_INSENSITIVE);
        String content = closing.matcher(section.getValue()).replaceAll(Matcher.quoteReplacement("<\\/" + name + ">"));
        return "<" + name + ">\n" + content + "\n</" + name + ">";
    }

    private record Parsed<T>(T value, List<String> problems) {

        static <T> Parsed<T> valid(T value) {
            return new Parsed<>(value, List.of());
        }

        static <T> Parsed<T> invalid(List<String> problems) {
            return new Parsed<>(null, List.copyOf(problems));
        }
    }
}
