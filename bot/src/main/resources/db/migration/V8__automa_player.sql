-- V8: Add automa flag to player_states so a synthetic AI opponent can be added to a game.
-- The automa takes its turn automatically after the preceding human player ends theirs.

ALTER TABLE player_states
    ADD COLUMN is_automa BOOLEAN NOT NULL DEFAULT FALSE;
