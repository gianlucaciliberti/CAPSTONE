package it.squadra.gestionale.enums;

/** Azioni registrate nell'audit log. Il nome viene salvato nella colonna audit_logs.action. */
public enum AuditAction {
    ADMIN_BOOTSTRAPPED,
    USER_REGISTERED,
    USER_APPROVED,
    USER_REJECTED,
    USER_SUSPENDED,
    USER_REACTIVATED,
    USER_ROLE_CHANGED
}
