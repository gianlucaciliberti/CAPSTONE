package it.squadra.gestionale.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

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
