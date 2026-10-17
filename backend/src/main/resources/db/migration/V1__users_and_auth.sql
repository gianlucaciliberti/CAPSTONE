-- Utenti, sessioni (refresh token) e token monouso (verifica email, reset password).
-- Tutti gli istanti sono TIMESTAMPTZ salvati in UTC; la conversione in Europe/Rome avviene nell'applicazione.

CREATE TABLE users (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name             VARCHAR(80)  NOT NULL,
    last_name              VARCHAR(80)  NOT NULL,
    email                  VARCHAR(254) NOT NULL,
    password_hash          VARCHAR(100) NOT NULL,
    role                   VARCHAR(20)  NOT NULL DEFAULT 'PLAYER',
    status                 VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    email_verified_at      TIMESTAMPTZ,
    failed_login_attempts  INTEGER      NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,
    status_changed_at      TIMESTAMPTZ,
    status_changed_by      BIGINT REFERENCES users (id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'COACH', 'PLAYER')),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_login_attempts >= 0)
);

CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_role_status ON users (role, status);

-- Refresh token opachi: nel database si salva solo l'hash SHA-256 (hex), mai il valore in chiaro.
-- family_id raggruppa i token ruotati della stessa sessione per rilevare il riuso di token già sostituiti.
CREATE TABLE refresh_tokens (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    token_hash      VARCHAR(64) NOT NULL,
    family_id       UUID        NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoked_reason  VARCHAR(40),
    replaced_by_id  BIGINT REFERENCES refresh_tokens (id),
    user_agent      VARCHAR(255),
    ip_address      VARCHAR(45),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- Token monouso inviati via email. Anche qui si conserva solo l'hash.
CREATE TABLE user_tokens (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    purpose       VARCHAR(30) NOT NULL,
    token_hash    VARCHAR(64) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    requested_ip  VARCHAR(45),
    CONSTRAINT uk_user_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_user_tokens_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_user_tokens_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_user_tokens_user_purpose ON user_tokens (user_id, purpose);
