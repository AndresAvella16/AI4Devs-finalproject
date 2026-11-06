package com.jobpilot.shared.error;

/**
 * Login con email o contraseña incorrectos (→ 401). El mensaje es siempre el mismo, exista o no el email,
 * para no revelar qué emails están registrados.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("El email o la contraseña no son correctos.");
    }
}
