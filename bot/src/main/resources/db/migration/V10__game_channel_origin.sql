-- Allow the same lobby channel to host multiple games over time.
-- Only one SETUP/ACTIVE/FINAL_SCORING game per origin channel is permitted at a time.
-- The thread_id column now stores the dedicated game channel ID (set after channel creation)
-- rather than the lobby channel, which is preserved in origin_channel_id.

ALTER TABLE games DROP CONSTRAINT games_thread_id_key;
ALTER TABLE games ALTER COLUMN thread_id DROP NOT NULL;

ALTER TABLE games ADD COLUMN origin_channel_id VARCHAR(20);
UPDATE games SET origin_channel_id = thread_id;

-- Partial unique index: only one active game per thread_id value at a time.
CREATE UNIQUE INDEX games_thread_id_active_idx
    ON games(thread_id)
    WHERE status NOT IN ('ENDED', 'ABANDONED');
