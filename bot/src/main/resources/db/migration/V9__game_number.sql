-- V9: Add a sequential game number so Discord channels can be named ARK-1, ARK-2, etc.
-- SERIAL creates a sequence and sets the column default to nextval(seq).
-- Existing rows (if any) are assigned sequential values automatically by PostgreSQL.

ALTER TABLE games ADD COLUMN game_number SERIAL NOT NULL;
