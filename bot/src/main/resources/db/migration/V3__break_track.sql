-- Add break_track column to player_states.
-- The break track records how many cards a player has broken (placed on the break pile)
-- during the game. Used for X token scoring at end game and some card ability conditions.
ALTER TABLE player_states
    ADD COLUMN IF NOT EXISTS break_track INTEGER NOT NULL DEFAULT 0;
