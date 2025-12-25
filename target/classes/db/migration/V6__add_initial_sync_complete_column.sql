-- V6: Add initial_sync_complete column to sync_checkpoints table
ALTER TABLE sync_checkpoints ADD COLUMN initial_sync_complete BOOLEAN DEFAULT FALSE;
