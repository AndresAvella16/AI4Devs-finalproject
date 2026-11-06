package com.jobpilot.auth.service;

import com.jobpilot.auth.domain.User;
import com.jobpilot.auth.repository.UserRepository;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.InvalidCredentialsException;
import com.jobpilot.shared.validation.PasswordPolicyValidator;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Registro y login. El email se normaliza a minúsculas antes de guardarlo y de buscarlo (sección 3.2). Ante un
 * email inexistente también se compara un hash BCrypt, para que el tiempo de respuesta no revele si existe.
 *
 * <p>Sin {@code @Transactional} a propósito: si {@code saveAndFlush} falla por la unicidad del email, la
 * excepción no debe dejar marcada una transacción externa para rollback; cada llamada al repositorio tiene la suya.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = encoder.encode("hash-de-relleno-para-igualar-tiempos-1");
    }

    public AuthResult register(String email, String password, String fullName) {
        String normalizedEmail = normalize(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw emailAlreadyRegistered();
        }
        User user = new User(normalizedEmail, encoder.encode(password), fullName.strip(), clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Otro registro con el mismo email ha ganado la carrera entre existsByEmail y el INSERT (uq_users_email).
            throw emailAlreadyRegistered();
        }
        return new AuthResult(user, tokens.issue(user.getId()));
    }

    public AuthResult login(String email, String password) {
        Optional<User> user = users.findByEmail(normalize(email));
        if (password.getBytes(StandardCharsets.UTF_8).length > PasswordPolicyValidator.MAX_BYTES) {
            // Nunca puede ser una contraseña registrada, y BCryptPasswordEncoder la rechazaría con una excepción.
            throw new InvalidCredentialsException();
        }
        if (user.isEmpty()) {
            encoder.matches(password, dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!encoder.matches(password, user.get().getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return new AuthResult(user.get(), tokens.issue(user.get().getId()));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static ConflictException emailAlreadyRegistered() {
        return new ConflictException("email-already-registered", "Email ya registrado",
                "Ya existe una cuenta con ese email.");
    }
}
