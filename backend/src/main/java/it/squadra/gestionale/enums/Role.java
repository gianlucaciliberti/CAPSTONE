package it.squadra.gestionale.enums;

/** Ruoli applicativi. I permessi sono cumulativi: ADMIN include COACH, che include PLAYER. */
public enum Role {
    PLAYER,
    COACH,
    ADMIN
}
