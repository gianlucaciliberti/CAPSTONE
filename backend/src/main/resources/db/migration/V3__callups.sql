-- Convocazioni: una per partita, con storico delle revisioni pubblicate.
-- Nessuna riga viene cancellata: i convocati rimossi restano con removed_at valorizzato
-- e ogni pubblicazione/modifica/ritiro produce una revisione con snapshot immutabile.

CREATE TABLE callups (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    match_id            BIGINT       NOT NULL REFERENCES matches (id),
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    meeting_at          TIMESTAMPTZ,
    notes               VARCHAR(2000),
    current_revision    INTEGER      NOT NULL DEFAULT 0,
    published_at        TIMESTAMPTZ,
    published_by        BIGINT REFERENCES users (id),
    withdrawn_at        TIMESTAMPTZ,
    withdrawn_by        BIGINT REFERENCES users (id),
    withdrawal_reason   VARCHAR(500),
    created_by          BIGINT       NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          BIGINT REFERENCES users (id),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_callups_match UNIQUE (match_id),
    CONSTRAINT ck_callups_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN')),
    CONSTRAINT ck_callups_revision CHECK (current_revision >= 0),
    CONSTRAINT ck_callups_published CHECK (status <> 'PUBLISHED' OR (published_at IS NOT NULL AND published_by IS NOT NULL)),
    CONSTRAINT ck_callups_withdrawn CHECK (status <> 'WITHDRAWN' OR (withdrawn_at IS NOT NULL AND withdrawn_by IS NOT NULL))
);

-- added_in_revision/removed_in_revision restano NULL finché la modifica è in bozza:
-- alla pubblicazione permettono di notificare solo gli utenti effettivamente interessati.
CREATE TABLE callup_players (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    callup_id            BIGINT       NOT NULL REFERENCES callups (id),
    user_id              BIGINT       NOT NULL REFERENCES users (id),
    added_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    added_by             BIGINT       NOT NULL REFERENCES users (id),
    added_in_revision    INTEGER,
    removed_at           TIMESTAMPTZ,
    removed_by           BIGINT REFERENCES users (id),
    removed_in_revision  INTEGER,
    CONSTRAINT ck_callup_players_removed CHECK ((removed_at IS NULL) = (removed_by IS NULL))
);

-- Un utente può comparire una sola volta tra i convocati attivi di una convocazione.
CREATE UNIQUE INDEX uk_callup_players_active ON callup_players (callup_id, user_id) WHERE removed_at IS NULL;
CREATE INDEX idx_callup_players_user ON callup_players (user_id);

CREATE TABLE callup_revisions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    callup_id        BIGINT        NOT NULL REFERENCES callups (id),
    revision_number  INTEGER       NOT NULL,
    action           VARCHAR(20)   NOT NULL,
    snapshot         JSONB         NOT NULL,
    change_summary   VARCHAR(1000),
    author_id        BIGINT        NOT NULL REFERENCES users (id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_callup_revisions_number UNIQUE (callup_id, revision_number),
    CONSTRAINT ck_callup_revisions_action CHECK (action IN ('PUBLISHED', 'UPDATED', 'WITHDRAWN')),
    CONSTRAINT ck_callup_revisions_number CHECK (revision_number > 0)
);
