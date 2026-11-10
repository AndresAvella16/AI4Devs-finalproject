package com.jobpilot.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * {@link ChatModel} programable para los tests: devuelve las respuestas en orden (la última se repite) y guarda
 * los prompts recibidos. Sustituye a un mock de Mockito, que tarda ~9 s en instrumentar la jerarquía de ChatModel.
 */
final class ScriptedChatModel implements ChatModel {

    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile List<Function<Prompt, ChatResponse>> script = List.of();

    /** Opciones por defecto, como las de {@code spring.ai.anthropic.chat.*} en el modelo real. */
    private final ChatOptions defaultOptions = ChatOptions.builder()
            .model("claude-test").maxTokens(4096).temperature(0.7).topK(5).build();

    /** Respuestas de texto sucesivas. */
    ScriptedChatModel respond(String... texts) {
        List<Function<Prompt, ChatResponse>> steps = new ArrayList<>();
        for (String text : texts) {
            steps.add(prompt -> text(text));
        }
        return script(steps);
    }

    /** Respuestas sucesivas ya construidas (p. ej. sin generaciones). */
    ScriptedChatModel respondWith(ChatResponse... responses) {
        List<Function<Prompt, ChatResponse>> steps = new ArrayList<>();
        for (ChatResponse response : responses) {
            steps.add(prompt -> response);
        }
        return script(steps);
    }

    /** Cada llamada lanza esta excepción. */
    ScriptedChatModel fail(RuntimeException error) {
        return script(List.of(prompt -> {
            throw error;
        }));
    }

    /** Cada llamada ejecuta este comportamiento (p. ej. bloquearse hasta que el test lo libere). */
    ScriptedChatModel answer(Function<Prompt, ChatResponse> behaviour) {
        return script(List.of(behaviour));
    }

    void reset() {
        prompts.clear();
        script = List.of();
    }

    int calls() {
        return prompts.size();
    }

    Prompt lastPrompt() {
        return prompts.getLast();
    }

    static ChatResponse text(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Override
    public ChatOptions getOptions() {
        return defaultOptions;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        int index;
        List<Function<Prompt, ChatResponse>> steps = script;
        synchronized (prompts) {
            prompts.add(prompt);
            index = prompts.size() - 1;
        }
        if (steps.isEmpty()) {
            throw new IllegalStateException("ScriptedChatModel sin respuesta programada");
        }
        return steps.get(Math.min(index, steps.size() - 1)).apply(prompt);
    }

    private ScriptedChatModel script(List<Function<Prompt, ChatResponse>> steps) {
        this.script = List.copyOf(steps);
        return this;
    }
}
