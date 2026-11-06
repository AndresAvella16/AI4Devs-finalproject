package com.jobpilot.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LLM simulado ({@code llm.mode=fake}, el modo por defecto): devuelve respuestas fijas de
 * {@code src/main/resources/fake-llm/*.json}. Se usa en desarrollo sin API key, en los tests de integración y en
 * los E2E. No aplica el límite de concurrencia: no tiene coste ni latencia.
 */
@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "fake", matchIfMissing = true)
public class FakeLlmGateway implements LlmGateway {

    static final String MODEL_NAME = "fake";

    @SuppressWarnings("unused") // la usan los métodos que añaden JP-BE-04 a JP-BE-07
    private final FakeResponses responses;

    FakeLlmGateway(FakeResponses responses) {
        this.responses = responses;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }
}
