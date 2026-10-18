package it.squadra.gestionale.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Password accettabile: almeno 10 caratteri, almeno una lettera e una cifra, al massimo 72 byte
 * (limite di BCrypt: i byte oltre il 72° verrebbero ignorati o rifiutati).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {

    String message() default "La password deve avere almeno 10 caratteri, con almeno una lettera e un numero.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
