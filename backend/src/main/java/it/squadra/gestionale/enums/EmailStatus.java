package it.squadra.gestionale.enums;

public enum EmailStatus {
    /** In coda, in attesa di invio o di un nuovo tentativo. */
    PENDING,
    /** Presa in carico da un worker (locked_until protegge da invii concorrenti). */
    SENDING,
    SENT,
    /** Tentativi esauriti: richiede un retry manuale dell'Admin. */
    FAILED,
    CANCELLED
}
