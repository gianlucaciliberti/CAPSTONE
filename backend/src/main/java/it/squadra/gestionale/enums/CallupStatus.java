package it.squadra.gestionale.enums;

public enum CallupStatus {
    /** Bozza, visibile solo ad Allenatori e Admin. */
    DRAFT,
    /** Pubblicata: visibile ai convocati, disponibilità bloccate. */
    PUBLISHED,
    /** Ritirata dopo la pubblicazione; lo storico resta consultabile. */
    WITHDRAWN
}
