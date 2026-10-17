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
    INTERNAL_ERROR
}
