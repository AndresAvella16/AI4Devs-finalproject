package com.jobpilot.auth.web;

import com.jobpilot.auth.service.AuthService;
import com.jobpilot.shared.ratelimit.RateLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints públicos de autenticación. Devuelven el JWT que el resto de la API exige como {@code Bearer}. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimits rateLimits;

    public AuthController(AuthService authService, RateLimits rateLimits) {
        this.authService = authService;
        this.rateLimits = rateLimits;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return AuthResponse.from(authService.register(request.email(), request.password(), request.fullName()));
    }

    /**
     * Máximo de intentos por IP + email (sección 2.5) → 429, antes de comprobar la contraseña. La IP es la del
     * cliente: detrás de nginx, Tomcat la toma de {@code X-Forwarded-For} solo si llega desde un proxy interno
     * ({@code server.forward-headers-strategy: native}).
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        rateLimits.consumeLoginAttempt(httpRequest.getRemoteAddr(), request.email());
        return AuthResponse.from(authService.login(request.email(), request.password()));
    }
}
