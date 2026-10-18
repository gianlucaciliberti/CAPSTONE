package it.squadra.gestionale.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    record Form(@StrongPassword String password) {
    }

    @ParameterizedTest
    @ValueSource(strings = {"Calcetto2026", "passwordlunga1", "1234567890a", "àèìòù12345"})
    void acceptsLongPasswordsWithLettersAndDigits(String password) {
        assertThat(VALIDATOR.validate(new Form(password))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corta1", "solamentelettere", "12345678901234"})
    void rejectsShortOrSingleClassPasswords(String password) {
        assertThat(VALIDATOR.validate(new Form(password))).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {73, 100})
    void rejectsPasswordsBeyondBcryptLimit(int length) {
        String tooLong = "a1".repeat(length / 2 + 1).substring(0, length);

        assertThat(VALIDATOR.validate(new Form(tooLong)))
                .singleElement()
                .extracting("message")
                .isEqualTo("La password è troppo lunga (massimo 72 caratteri).");
    }

    @Test
    void rejectsNull() {
        assertThat(VALIDATOR.validate(new Form(null))).hasSize(1);
    }
}
