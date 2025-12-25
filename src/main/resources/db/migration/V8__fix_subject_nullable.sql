-- Fix subject field to allow null values

ALTER TABLE mvp_migrated_email ALTER COLUMN subject DROP NOT NULL;
