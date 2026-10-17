-- Partite e disponibilità dei giocatori.

CREATE TABLE matches (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    opponent             VARCHAR(120)  NOT NULL,
    kickoff_at           TIMESTAMPTZ   NOT NULL,
    venue_name           VARCHAR(150)  NOT NULL,
    venue_address        VARCHAR(255)  NOT NULL,
    notes                VARCHAR(2000),
    status               VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',
    team_goals           INTEGER,
    opponent_goals       INTEGER,
    result_updated_at    TIMESTAMPTZ,
    result_updated_by    BIGINT REFERENCES users (id),
    cancelled_at         TIMESTAMPTZ,
    cancelled_by         BIGINT REFERENCES users (id),
    cancellation_reason  VARCHAR(500),
    created_by           BIGINT        NOT NULL REFERENCES users (id),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by           BIGINT REFERENCES users (id),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_matches_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_matches_score_pair CHECK ((team_goals IS NULL) = (opponent_goals IS NULL)),
    CONSTRAINT ck_matches_score_non_negative CHECK (team_goals IS NULL OR (team_goals >= 0 AND opponent_goals >= 0)),
    CONSTRAINT ck_matches_cancelled CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL))
);

CREATE INDEX idx_matches_kickoff ON matches (kickoff_at);
CREATE INDEX idx_matches_status_kickoff ON matches (status, kickoff_at);

-- Una riga per (partita, utente). L'assenza della riga equivale a "in attesa di risposta" (PENDING).
-- admin_override indica una correzione amministrativa dopo la pubblicazione della convocazione.
CREATE TABLE availabilities (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    match_id        BIGINT       NOT NULL REFERENCES matches (id),
    user_id         BIGINT       NOT NULL REFERENCES users (id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    note            VARCHAR(500),
    admin_override  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      BIGINT       NOT NULL REFERENCES users (id),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_availabilities_match_user UNIQUE (match_id, user_id),
    CONSTRAINT ck_availabilities_status CHECK (status IN ('AVAILABLE', 'UNAVAILABLE', 'PENDING'))
);

CREATE INDEX idx_availabilities_user ON availabilities (user_id);
