-- Add Discord channel ID columns for per-game channel architecture.
-- These are populated when a game starts (via DiscordChannelService.setupGameChannels).
-- Nullable because games created before this migration won't have channels.
ALTER TABLE games
    ADD COLUMN IF NOT EXISTS category_id   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS board_channel_id VARCHAR(20);

ALTER TABLE player_states
    ADD COLUMN IF NOT EXISTS private_channel_id VARCHAR(20);
