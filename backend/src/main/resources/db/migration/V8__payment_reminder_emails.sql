-- Promemoria per le quote in scadenza o scadute: nuovo tipo di email.

ALTER TABLE email_notifications DROP CONSTRAINT ck_email_notifications_type;

ALTER TABLE email_notifications
    ADD CONSTRAINT ck_email_notifications_type CHECK (notification_type IN (
        'CALLUP_PUBLISHED', 'CALLUP_UPDATED', 'CALLUP_WITHDRAWN', 'CALLUP_REMOVED',
        'ACCOUNT_APPROVED', 'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'PAYMENT_REQUEST', 'PAYMENT_REMINDER'));
