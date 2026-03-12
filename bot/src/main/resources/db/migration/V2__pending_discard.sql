ALTER TABLE player_states
    ADD COLUMN pending_discard_count SMALLINT NOT NULL DEFAULT 0;
