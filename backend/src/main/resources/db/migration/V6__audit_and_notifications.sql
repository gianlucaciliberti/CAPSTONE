-- Audit log append-only e coda persistente delle email (transactional outbox).

CREATE TABLE audit_logs (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actor_id     BIGINT REFERENCES users (id),
    actor_email  VARCHAR(254),
    action       VARCHAR(60)   NOT NULL,
    entity_type  VARCHAR(40)   NOT NULL,
    entity_id    VARCHAR(64),
    summary      VARCHAR(500),
    details      JSONB,
    ip_address   VARCHAR(45)
);

CREATE INDEX idx_audit_logs_occurred ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);

-- L'audit log non può essere modificato né cancellato dall'applicazione.
CREATE FUNCTION prevent_audit_log_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_modification();

CREATE TRIGGER trg_audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_audit_log_modification();

-- Ogni riga è sia la notifica sia l'elemento della coda: viene inserita nella stessa transazione
-- dell'operazione di business e un worker la invia in seguito, con retry limitati.
-- idempotency_key impedisce di accodare due volte la stessa email.
CREATE TABLE email_notifications (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key      VARCHAR(150)  NOT NULL,
    notification_type    VARCHAR(40)   NOT NULL,
    recipient_user_id    BIGINT REFERENCES users (id),
    recipient_email      VARCHAR(254)  NOT NULL,
    recipient_name       VARCHAR(170),
    subject              VARCHAR(200)  NOT NULL,
    payload              JSONB         NOT NULL DEFAULT '{}'::jsonb,
    related_entity_type  VARCHAR(40),
    related_entity_id    VARCHAR(64),
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempts             INTEGER       NOT NULL DEFAULT 0,
    max_attempts         INTEGER       NOT NULL DEFAULT 5,
    next_attempt_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    locked_until         TIMESTAMPTZ,
    last_error           VARCHAR(1000),
    provider_message_id  VARCHAR(100),
    sent_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_email_notifications_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_email_notifications_type CHECK (notification_type IN (
        'CALLUP_PUBLISHED', 'CALLUP_UPDATED', 'CALLUP_WITHDRAWN', 'CALLUP_REMOVED',
        'ACCOUNT_APPROVED', 'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'PAYMENT_REQUEST')),
    CONSTRAINT ck_email_notifications_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_email_notifications_attempts CHECK (attempts >= 0 AND max_attempts > 0)
);

CREATE INDEX idx_email_notifications_queue ON email_notifications (next_attempt_at)
    WHERE status IN ('PENDING', 'SENDING');
CREATE INDEX idx_email_notifications_status ON email_notifications (status, created_at DESC);
CREATE INDEX idx_email_notifications_recipient ON email_notifications (recipient_user_id);
CREATE INDEX idx_email_notifications_related ON email_notifications (related_entity_type, related_entity_id);
