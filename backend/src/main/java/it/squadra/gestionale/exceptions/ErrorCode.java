package it.squadra.gestionale.exceptions;

/** Codici di errore stabili restituiti nel campo {@code code} delle risposte di errore, utili al frontend. */
public enum ErrorCode {
    VALIDATION_FAILED,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    CONCURRENT_MODIFICATION,
    BUSINESS_RULE_VIOLATION,
    TOO_MANY_REQUESTS,
    INTERNAL_ERROR,
    /** Login: account in attesa di approvazione. */
    ACCOUNT_PENDING,
    /** Login: registrazione non approvata. */
    ACCOUNT_REJECTED,
    /** Login: account sospeso da un amministratore. */
    ACCOUNT_SUSPENDED,
    /** Login: account bloccato temporaneamente per troppi tentativi falliti. */
    ACCOUNT_LOCKED
}
