package com.jobpilot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador real ({@code llm.mode=real}): Claude a través de Spring AI. Cada método construye un
 * {@link StructuredRequest} y lo ejecuta con {@link StructuredLlmCall}, que aplica el límite de concurrencia, la
 * validación con un reintento y la traducción de errores del proveedor.
 */
@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
public class SpringAiLlmGateway implements LlmGateway {

    @SuppressWarnings("unused") // la usan los métodos que añaden JP-BE-04 a JP-BE-07
    private final StructuredLlmCall call;

    private final String modelName;

    SpringAiLlmGateway(StructuredLlmCall call, @Value("${spring.ai.anthropic.chat.model}") String modelName) {
        this.call = call;
        this.modelName = modelName;
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
