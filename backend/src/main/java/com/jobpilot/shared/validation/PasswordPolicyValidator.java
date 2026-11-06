package com.jobpilot.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** Implementa {@link ValidPassword}. Cuenta caracteres Unicode (code points) para el mínimo y bytes UTF-8 para el máximo. */
public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    static final int MIN_CHARACTERS = 8;

    /** BCrypt solo usa los primeros 72 bytes; el {@code BCryptPasswordEncoder} rechaza contraseñas más largas. */
    public static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value.codePointCount(0, value.length()) < MIN_CHARACTERS) {
            return false;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return false;
        }
        return value.codePoints().anyMatch(Character::isLetter) && value.codePoints().anyMatch(Character::isDigit);
    }
}
