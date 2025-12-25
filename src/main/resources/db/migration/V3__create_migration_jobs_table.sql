-- V3: Create migration_jobs table
CREATE TABLE migration_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_account_id BIGINT NOT NULL,
    target_account_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    phase VARCHAR(30) NOT NULL DEFAULT 'INITIAL_SYNC',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    data_types_config TEXT DEFAULT '{"emails":true,"contacts":true,"calendars":true}',

    -- Email statistics
    total_emails BIGINT DEFAULT 0,
    migrated_emails BIGINT DEFAULT 0,
    failed_emails BIGINT DEFAULT 0,

    -- Contact statistics
    total_contacts BIGINT DEFAULT 0,
    migrated_contacts BIGINT DEFAULT 0,
    failed_contacts BIGINT DEFAULT 0,

    -- Calendar event statistics
    total_events BIGINT DEFAULT 0,
    migrated_events BIGINT DEFAULT 0,
    failed_events BIGINT DEFAULT 0,

    overall_progress_percent INT DEFAULT 0,
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_migration_jobs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_migration_jobs_source FOREIGN KEY (source_account_id) REFERENCES email_accounts(id),
    CONSTRAINT fk_migration_jobs_target FOREIGN KEY (target_account_id) REFERENCES email_accounts(id)
);

CREATE INDEX idx_migration_job_user ON migration_jobs(user_id);
CREATE INDEX idx_migration_job_status ON migration_jobs(status);
CREATE INDEX idx_migration_job_phase ON migration_jobs(phase);
CREATE INDEX idx_migration_job_scheduled ON migration_jobs(scheduled_at);
