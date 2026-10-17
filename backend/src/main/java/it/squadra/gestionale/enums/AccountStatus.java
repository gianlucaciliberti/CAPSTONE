package it.squadra.gestionale.enums;

/** Stato dell'account, indipendente dal ruolo. Solo gli account ACTIVE accedono alle funzionalità protette. */
public enum AccountStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    SUSPENDED
}
