-- Pagamenti manuali. Importi in centesimi di euro (INTEGER), mai in virgola mobile.
-- Le richieste saldate o annullate non vengono mai cancellate: cambiano solo stato.

CREATE TABLE payment_requests (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    description          VARCHAR(200)  NOT NULL,
    amount_cents         INTEGER       NOT NULL,
    currency             VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    due_date             DATE,
    notes                VARCHAR(1000),
    status               VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    created_by           BIGINT        NOT NULL REFERENCES users (id),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by           BIGINT REFERENCES users (id),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cancelled_at         TIMESTAMPTZ,
    cancelled_by         BIGINT REFERENCES users (id),
    cancellation_reason  VARCHAR(500),
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_requests_amount CHECK (amount_cents > 0),
    CONSTRAINT ck_payment_requests_currency CHECK (currency = 'EUR'),
    CONSTRAINT ck_payment_requests_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_payment_requests_cancelled CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL))
);

CREATE INDEX idx_payment_requests_status ON payment_requests (status);
CREATE INDEX idx_payment_requests_due_date ON payment_requests (due_date);

-- Una riga per ogni utente destinatario della richiesta.
CREATE TABLE payment_assignments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_request_id  BIGINT        NOT NULL REFERENCES payment_requests (id),
    user_id             BIGINT        NOT NULL REFERENCES users (id),
    amount_cents        INTEGER       NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    paid_on             DATE,
    payment_method      VARCHAR(20),
    confirmed_at        TIMESTAMPTZ,
    confirmed_by        BIGINT REFERENCES users (id),
    admin_note          VARCHAR(500),
    cancelled_at        TIMESTAMPTZ,
    cancelled_by        BIGINT REFERENCES users (id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_assignments_request_user UNIQUE (payment_request_id, user_id),
    CONSTRAINT ck_payment_assignments_amount CHECK (amount_cents > 0),
    CONSTRAINT ck_payment_assignments_status CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_payment_assignments_method CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT ck_payment_assignments_paid CHECK (status <> 'PAID' OR (paid_on IS NOT NULL AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL)),
    CONSTRAINT ck_payment_assignments_cancelled CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL))
);

CREATE INDEX idx_payment_assignments_user_status ON payment_assignments (user_id, status);
