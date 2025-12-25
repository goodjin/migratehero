-- Add error details columns to MVP migration task table

ALTER TABLE mvp_migration_task
ADD COLUMN failed_endpoint VARCHAR(1000) NULL AFTER error_message;

ALTER TABLE mvp_migration_task
ADD COLUMN failed_request VARCHAR(4000) NULL AFTER failed_endpoint;

ALTER TABLE mvp_migration_task
ADD COLUMN failed_response LONGTEXT NULL AFTER failed_request;
