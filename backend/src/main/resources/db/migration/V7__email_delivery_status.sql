-- Esito della consegna comunicato dai webhook Mailjet (dopo che l'invio è stato accettato dal provider).
-- delivery_status: ultimo evento ricevuto (es. SENT, OPEN, CLICK, BOUNCE, BLOCKED, SPAM, UNSUB).

ALTER TABLE email_notifications
    ADD COLUMN delivery_status VARCHAR(20),
    ADD COLUMN delivery_updated_at TIMESTAMPTZ,
    ADD COLUMN delivery_error VARCHAR(500);

ALTER TABLE email_notifications
    ADD CONSTRAINT ck_email_notifications_delivery_status CHECK (delivery_status IS NULL OR delivery_status IN (
        'SENT', 'OPEN', 'CLICK', 'BOUNCE', 'BLOCKED', 'SPAM', 'UNSUB'));

CREATE INDEX idx_email_notifications_provider_message ON email_notifications (provider_message_id);
