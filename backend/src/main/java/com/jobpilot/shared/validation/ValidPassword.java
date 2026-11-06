package com.jobpilot.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Política de contraseñas (sección 2.5): al menos 8 caracteres, con al menos una letra y un número, y como máximo
 * 72 bytes en UTF-8 (BCrypt no usa más). {@code null} se considera válido: combínala con {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "debe tener al menos 8 caracteres, con letras y números, y no superar los 72 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
