package it.squadra.gestionale.enums;

public enum PaymentRequestStatus {
    /** Almeno un destinatario deve ancora pagare. */
    OPEN,
    /** Tutte le quote sono state saldate o annullate. */
    CLOSED,
    CANCELLED
}
