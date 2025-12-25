-- V5: Create migration_logs table
CREATE TABLE migration_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    level VARCHAR(10) NOT NULL,
    data_type VARCHAR(20),
    message TEXT NOT NULL,
    item_id VARCHAR(255),
    items_count BIGINT,
    execution_time_ms BIGINT,
    error_details TEXT,
    stack_trace TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_migration_logs_job FOREIGN KEY (job_id) REFERENCES migration_jobs(id) ON DELETE CASCADE
);

CREATE INDEX idx_migration_log_job ON migration_logs(job_id);
CREATE INDEX idx_migration_log_level ON migration_logs(level);
CREATE INDEX idx_migration_log_created ON migration_logs(created_at);
CREATE INDEX idx_migration_log_data_type ON migration_logs(data_type);
