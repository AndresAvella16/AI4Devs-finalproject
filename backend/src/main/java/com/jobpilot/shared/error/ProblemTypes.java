package com.jobpilot.shared.error;

import java.net.URI;

/** Catálogo de tipos de problema (RFC 9457) de la API. Ver la sección 4 del readme. */
public final class ProblemTypes {

    public static final String BASE = "https://jobpilot.dev/problems/";

    public static final URI VALIDATION_ERROR = of("validation-error");
    public static final URI MALFORMED_REQUEST = of("malformed-request");
    public static final URI NOT_FOUND = of("not-found");
    public static final URI LLM_TIMEOUT = of("llm-timeout");
    public static final URI LLM_OVERLOADED = of("llm-overloaded");
    public static final URI LLM_INVALID_RESPONSE = of("llm-invalid-response");
    public static final URI INTERNAL_ERROR = of("internal-error");

    private ProblemTypes() {
    }

    public static URI of(String slug) {
        return URI.create(BASE + slug);
    }
}
