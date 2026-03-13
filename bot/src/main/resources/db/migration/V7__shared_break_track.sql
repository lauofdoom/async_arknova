-- Migrate break_track from per-player to shared (one value per game on shared_board_state).

ALTER TABLE shared_board_state
    ADD COLUMN IF NOT EXISTS break_track INTEGER NOT NULL DEFAULT 0;

ALTER TABLE player_states
    DROP COLUMN IF EXISTS break_track;
