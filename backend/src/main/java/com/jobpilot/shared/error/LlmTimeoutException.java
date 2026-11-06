package com.jobpilot.shared.error;

/** El LLM no ha respondido dentro del tiempo máximo (→ 504). El mensaje solo va al log, nunca a la respuesta. */
public class LlmTimeoutException extends RuntimeException {

    public LlmTimeoutException(String message) {
        super(message);
    }

    public LlmTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
