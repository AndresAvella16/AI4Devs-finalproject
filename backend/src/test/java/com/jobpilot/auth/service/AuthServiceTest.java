package com.jobpilot.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.auth.domain.User;
import com.jobpilot.auth.repository.UserRepository;
import com.jobpilot.shared.error.ConflictException;
import com.jobpilot.shared.error.InvalidCredentialsException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final String DUMMY_HASH = "$2a$12$dummy";

    private final UserRepository users = Mockito.mock(UserRepository.class);
    private final PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
    private final TokenService tokens = Mockito.mock(TokenService.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        when(encoder.encode(anyString())).thenAnswer(inv -> "hash(" + inv.getArgument(0) + ")");
        when(tokens.issue(any())).thenReturn(new IssuedToken("jwt", 3600));
        service = new AuthService(users, encoder, tokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User existing(String email, String hash) {
        User user = new User(email, hash, "Ana Test", NOW.minusSeconds(60));
        when(users.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void registrarNormalizaElEmailHasheaLaContrasenaYEmiteUnToken() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResult result = service.register("  Ana.Garcia@Test.DEV ", "Demo1234!", "  Ana García ");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana.garcia@test.dev");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash(Demo1234!)");
        assertThat(saved.getValue().getFullName()).isEqualTo("Ana García");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(result.user()).isSameAs(saved.getValue());
        assertThat(result.token().value()).isEqualTo("jwt");
        verify(tokens).issue(saved.getValue().getId());
    }

    @Test
    void registrarUnEmailYaExistenteEsConflicto() {
        when(users.existsByEmail("ana@test.dev")).thenReturn(true);

        assertThatThrownBy(() -> service.register("ANA@test.dev", "Demo1234!", "Ana"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getTypeSlug()).isEqualTo("email-already-registered"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void siOtroRegistroGanaLaCarreraTambienEsConflicto() {
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> service.register("ana@test.dev", "Demo1234!", "Ana"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getTypeSlug()).isEqualTo("email-already-registered"));
    }

    @Test
    void loginCorrectoSinDistinguirMayusculasEmiteUnToken() {
        User user = existing("ana@test.dev", "hash-ana");
        when(encoder.matches("Demo1234!", "hash-ana")).thenReturn(true);

        AuthResult result = service.login("Ana@Test.dev", "Demo1234!");

        assertThat(result.user()).isSameAs(user);
        verify(tokens).issue(user.getId());
    }

    @Test
    void loginConContrasenaIncorrectaSonCredencialesIncorrectas() {
        existing("ana@test.dev", "hash-ana");
        when(encoder.matches("otra", "hash-ana")).thenReturn(false);

        assertThatThrownBy(() -> service.login("ana@test.dev", "otra"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokens, never()).issue(any());
    }

    @Test
    void loginConEmailInexistenteTambienComparaUnHashParaNoRevelarloPorElTiempo() {
        when(users.findByEmail("nadie@test.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nadie@test.dev", "Demo1234!"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(encoder).matches(eq("Demo1234!"), anyString());
        verify(tokens, never()).issue(any());
    }

    @Test
    void loginConUnaContrasenaDeMasDe72BytesSonCredencialesIncorrectasSinLlegarABcrypt() {
        existing("ana@test.dev", "hash-ana");

        assertThatThrownBy(() -> service.login("ana@test.dev", "a".repeat(73)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(encoder, never()).matches(eq("a".repeat(73)), anyString());
    }

    @Test
    void elMensajeDeCredencialesIncorrectasEsElMismoExistaONoElEmail() {
        existing("ana@test.dev", "hash-ana");
        when(users.findByEmail("nadie@test.dev")).thenReturn(Optional.empty());

        String wrongPassword = catchMessage(() -> service.login("ana@test.dev", "otra"));
        String unknownEmail = catchMessage(() -> service.login("nadie@test.dev", "otra"));

        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("se esperaba InvalidCredentialsException");
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
    }

    @Test
    void noUsaUnHashDummyConocidoComoContrasenaValida() {
        when(users.findByEmail("nadie@test.dev")).thenReturn(Optional.empty());
        when(encoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.login("nadie@test.dev", DUMMY_HASH))
                .as("aunque el hash dummy coincida, un email inexistente nunca autentica")
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
