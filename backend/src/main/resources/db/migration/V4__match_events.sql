-- Tabellino: eventi della partita.
-- team_side indica la squadra del giocatore che compie l'evento:
--   GOAL     + TEAM     => gol nostro         GOAL     + OPPONENT => gol avversario
--   OWN_GOAL + TEAM     => gol avversario     OWN_GOAL + OPPONENT => gol nostro
-- I giocatori avversari non hanno account: si usa player_name (facoltativo).

CREATE TABLE match_events (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    match_id      BIGINT       NOT NULL REFERENCES matches (id),
    event_type    VARCHAR(20)  NOT NULL,
    team_side     VARCHAR(10)  NOT NULL,
    player_id     BIGINT REFERENCES users (id),
    player_name   VARCHAR(120),
    event_minute  INTEGER,
    notes         VARCHAR(500),
    created_by    BIGINT       NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    BIGINT REFERENCES users (id),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_match_events_type CHECK (event_type IN ('GOAL', 'OWN_GOAL', 'YELLOW_CARD', 'RED_CARD')),
    CONSTRAINT ck_match_events_side CHECK (team_side IN ('TEAM', 'OPPONENT')),
    CONSTRAINT ck_match_events_opponent_no_account CHECK (team_side = 'TEAM' OR player_id IS NULL),
    CONSTRAINT ck_match_events_single_identity CHECK (player_id IS NULL OR player_name IS NULL),
    CONSTRAINT ck_match_events_minute CHECK (event_minute IS NULL OR event_minute BETWEEN 0 AND 150)
);

CREATE INDEX idx_match_events_match ON match_events (match_id);
CREATE INDEX idx_match_events_player ON match_events (player_id);
