package com.jobpilot.shared.error;

/** El recurso no existe o no pertenece al usuario autenticado (→ 404, nunca 403). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String detail) {
        super(detail);
    }
}
