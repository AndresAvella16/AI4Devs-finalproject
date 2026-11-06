package com.jobpilot.shared.error;

/** El LLM ha devuelto una respuesta no válida también tras el reintento (→ 502). El mensaje solo va al log. */
public class LlmInvalidResponseException extends RuntimeException {

    public LlmInvalidResponseException(String message) {
        super(message);
    }

    public LlmInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
