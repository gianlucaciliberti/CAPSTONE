package it.squadra.gestionale.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Password accettabile: almeno 10 caratteri, almeno una lettera e una cifra, al massimo 72 byte
 * (limite di BCrypt: i byte oltre il 72° verrebbero ignorati o rifiutati).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPassword.Validator.class)
public @interface StrongPassword {

    String message() default "La password deve avere almeno 10 caratteri, con almeno una lettera e un numero.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<StrongPassword, String> {

        static final int MIN_LENGTH = 10;
        static final int MAX_BYTES = 72;

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("La password è troppo lunga (massimo 72 caratteri).")
                        .addConstraintViolation();
                return false;
            }
            return value.codePointCount(0, value.length()) >= MIN_LENGTH
                    && value.chars().anyMatch(Character::isLetter)
                    && value.chars().anyMatch(Character::isDigit);
        }
    }
}
