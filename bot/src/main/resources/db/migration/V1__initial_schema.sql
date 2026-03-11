-- ─────────────────────────────────────────────────────────────────────────────
-- Ark Nova Async — Initial Schema
-- Migration V1 — created by Flyway at startup
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Extensions ────────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- fuzzy text search (card browser)

-- ── Card Definitions ──────────────────────────────────────────────────────────
-- Populated at startup from cards/base_game.json by CardDatabaseLoader.
-- The card database is seeded from the Next-Ark-Nova-Cards community repository.
CREATE TABLE card_definitions (
    id              VARCHAR(20) PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    card_type       VARCHAR(20)  NOT NULL,   -- ANIMAL | SPONSOR | CONSERVATION | FINAL_SCORING
    base_cost       SMALLINT     NOT NULL DEFAULT 0,
    min_enclosure_size SMALLINT,             -- animals only; null for sponsors/projects
    tags            VARCHAR(20)[]  NOT NULL DEFAULT '{}',
    requirements    VARCHAR(20)[]  NOT NULL DEFAULT '{}',
    appeal_value    SMALLINT     NOT NULL DEFAULT 0,
    conservation_value SMALLINT  NOT NULL DEFAULT 0,
    reputation_value   SMALLINT  NOT NULL DEFAULT 0,

    -- Display text from Next-Ark-Nova-Cards (always present)
    ability_text    TEXT,

    -- Machine-executable effect (null = manual resolution required)
    -- Schema: { "abilities": [ { "trigger", "type", "resource", "amount", ... } ] }
    effect_code     JSONB,

    image_url       VARCHAR(500),
    source          VARCHAR(20)  NOT NULL DEFAULT 'BASE',
    card_number     VARCHAR(10),

    -- Full-text search vector (auto-maintained)
    search_vec      TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english', name || ' ' || COALESCE(ability_text, ''))
    ) STORED
);

CREATE INDEX idx_cards_type       ON card_definitions(card_type);
CREATE INDEX idx_cards_source     ON card_definitions(source);
CREATE INDEX idx_cards_tags       ON card_definitions USING GIN(tags);
CREATE INDEX idx_cards_search     ON card_definitions USING GIN(search_vec);
CREATE INDEX idx_cards_automated  ON card_definitions((effect_code IS NOT NULL));

-- ── Games ─────────────────────────────────────────────────────────────────────
CREATE TABLE games (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    guild_id                VARCHAR(20)  NOT NULL,
    thread_id               VARCHAR(20)  NOT NULL UNIQUE,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'SETUP',
                            -- SETUP | ACTIVE | FINAL_SCORING | ENDED | ABANDONED
    current_seat            SMALLINT     NOT NULL DEFAULT 0,
    turn_number             INTEGER      NOT NULL DEFAULT 0,
    final_scoring_triggered BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Flexible game settings: { "playerCount", "startingMoneyOverride", "mapIds[]" }
    settings                JSONB        NOT NULL DEFAULT '{}',

    -- Optimistic locking counter (incremented by JPA @Version on every save)
    state_version           INTEGER      NOT NULL DEFAULT 0,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_at              TIMESTAMPTZ,
    ended_at                TIMESTAMPTZ
);

CREATE INDEX idx_games_guild        ON games(guild_id);
CREATE INDEX idx_games_status       ON games(status);
CREATE INDEX idx_games_thread       ON games(thread_id);

-- ── Player States ─────────────────────────────────────────────────────────────
CREATE TABLE player_states (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id                 UUID         NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    discord_id              VARCHAR(20)  NOT NULL,
    discord_name            VARCHAR(100) NOT NULL,
    seat_index              SMALLINT     NOT NULL,      -- 0-based turn order
    map_id                  VARCHAR(20)  NOT NULL DEFAULT 'MAP_1',

    -- Scoring tracks
    appeal                  SMALLINT     NOT NULL DEFAULT 0,
    conservation            SMALLINT     NOT NULL DEFAULT 0,

    -- Resources
    money                   SMALLINT     NOT NULL DEFAULT 25,
    x_tokens                SMALLINT     NOT NULL DEFAULT 0,
    reputation              SMALLINT     NOT NULL DEFAULT 0,
    zoo_keepers_capacity    SMALLINT     NOT NULL DEFAULT 0,
    assoc_workers           SMALLINT     NOT NULL DEFAULT 3,
    assoc_workers_available SMALLINT     NOT NULL DEFAULT 3,

    -- Action card system
    -- Ordered left-to-right; index 0 = leftmost = strength 1
    action_card_order       VARCHAR(15)[] NOT NULL
                            DEFAULT ARRAY['CARDS','BUILD','ANIMALS','ASSOCIATION','SPONSOR'],
    upgraded_actions        VARCHAR(15)[] NOT NULL DEFAULT '{}',

    -- Zoo board layout (JSONB — grows as enclosures/animals are added)
    board_state             JSONB        NOT NULL DEFAULT
                            '{"enclosures":[],"kioskCount":0,"pavilionBuilt":false,"specialBuildings":[]}',

    -- Conservation board allocations
    conservation_slots      JSONB        NOT NULL DEFAULT
                            '{"projects":[null,null],"partnerZoos":[null,null,null],"universities":[null,null]}',

    -- Collected tag icons (for synergy calculation)
    -- e.g. { "AFRICA": 3, "PREDATOR": 2 }
    icons                   JSONB        NOT NULL DEFAULT '{}',

    -- End game
    final_scoring_done      BOOLEAN      NOT NULL DEFAULT FALSE,
    final_vp                JSONB,       -- populated at game end

    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_player_states_game_discord UNIQUE (game_id, discord_id),
    CONSTRAINT uq_player_states_game_seat    UNIQUE (game_id, seat_index)
);

CREATE INDEX idx_player_states_game         ON player_states(game_id);
CREATE INDEX idx_player_states_game_discord ON player_states(game_id, discord_id);

-- ── Shared Board State ────────────────────────────────────────────────────────
-- One row per game. Created when the game starts.
CREATE TABLE shared_board_state (
    game_id             UUID         PRIMARY KEY REFERENCES games(id) ON DELETE CASCADE,

    -- Card display (face-up market, 6 slots): ordered array of card IDs (null = empty)
    display_slots       VARCHAR(20)[] NOT NULL DEFAULT '{}',

    -- Remaining shuffled deck order (card IDs in draw order)
    animal_deck         VARCHAR(20)[] NOT NULL DEFAULT '{}',
    sponsor_deck        VARCHAR(20)[] NOT NULL DEFAULT '{}',
    conservation_deck   VARCHAR(20)[] NOT NULL DEFAULT '{}',

    -- Association board state
    -- { "slots": [ { "card_id": "...", "owner_discord_id": "..." }, ... ] }
    partner_zoo_slots   JSONB        NOT NULL DEFAULT '{"slots":[]}',
    university_slots    JSONB        NOT NULL DEFAULT '{"slots":[]}',

    -- Conservation project states
    -- { "12": { "status": "available", "slots": [null,null,null], "contributors": [] } }
    conservation_board  JSONB        NOT NULL DEFAULT '{"projects":{}}',

    -- Break tokens for multiplayer tiebreaking
    break_tokens        JSONB        NOT NULL DEFAULT '{}',

    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── Player Cards ──────────────────────────────────────────────────────────────
-- Tracks each card instance's location within a game for one player.
CREATE TABLE player_cards (
    id          BIGSERIAL    PRIMARY KEY,
    game_id     UUID         NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    discord_id  VARCHAR(20)  NOT NULL,
    card_id     VARCHAR(20)  NOT NULL REFERENCES card_definitions(id),
    location    VARCHAR(20)  NOT NULL,
                -- DECK | DISPLAY | HAND | PLAYED | PLACED | DISCARD
    enclosure_ref VARCHAR(10),           -- set when location = PLACED
    sort_order  SMALLINT                 -- ordering within location
);

CREATE INDEX idx_player_cards_game_player_loc
    ON player_cards(game_id, discord_id, location);

-- ── Action Log ────────────────────────────────────────────────────────────────
-- Immutable audit trail of every player action.
CREATE TABLE action_log (
    id                  BIGSERIAL    PRIMARY KEY,
    game_id             UUID         NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    turn_number         INTEGER      NOT NULL,
    discord_id          VARCHAR(20)  NOT NULL,
    discord_name        VARCHAR(100) NOT NULL,
    action_type         VARCHAR(30)  NOT NULL,
    action_card         VARCHAR(15),     -- which of the 5 action cards was used
    strength_used       SMALLINT,        -- strength (1-5) at time of use
    params              JSONB        NOT NULL DEFAULT '{}',
    result              JSONB,           -- state delta (populated after resolution)
    requires_manual     BOOLEAN      NOT NULL DEFAULT FALSE,
    discord_message_id  VARCHAR(20),
    snapshot_id         UUID,            -- FK to state_snapshots (set when undo is possible)
    ts                  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_action_log_game_turn   ON action_log(game_id, turn_number);
CREATE INDEX idx_action_log_game_player ON action_log(game_id, discord_id);
CREATE INDEX idx_action_log_snapshot    ON action_log(snapshot_id) WHERE snapshot_id IS NOT NULL;

-- ── State Snapshots (Undo) ────────────────────────────────────────────────────
-- Full compressed game state snapshots for undo support.
-- Maximum 3 snapshots retained per game (cleaned up by GameService after each action).
CREATE TABLE state_snapshots (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id     UUID         NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    turn_number INTEGER      NOT NULL,
    state_blob  BYTEA        NOT NULL,   -- gzip-compressed JSON of full game state
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_snapshots_game_turn ON state_snapshots(game_id, turn_number DESC);

-- ── Trigger: keep state_snapshots to max 3 per game ──────────────────────────
CREATE OR REPLACE FUNCTION trim_snapshots()
RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM state_snapshots
    WHERE game_id = NEW.game_id
      AND id NOT IN (
          SELECT id FROM state_snapshots
          WHERE game_id = NEW.game_id
          ORDER BY turn_number DESC
          LIMIT 3
      );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_trim_snapshots
    AFTER INSERT ON state_snapshots
    FOR EACH ROW EXECUTE FUNCTION trim_snapshots();

-- ── Updated-at trigger for player_states ─────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_player_states_updated_at
    BEFORE UPDATE ON player_states
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_shared_board_updated_at
    BEFORE UPDATE ON shared_board_state
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
