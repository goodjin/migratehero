-- V4: Create sync_checkpoints table
CREATE TABLE sync_checkpoints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    data_type VARCHAR(20) NOT NULL,
    last_sync_time TIMESTAMP,
    next_page_token TEXT,
    history_id VARCHAR(255),
    delta_token TEXT,
    processed_count BIGINT DEFAULT 0,
    batch_total_count BIGINT,
    last_item_id VARCHAR(255),
    state_data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_sync_checkpoints_job FOREIGN KEY (job_id) REFERENCES migration_jobs(id) ON DELETE CASCADE,
    CONSTRAINT uk_job_data_type UNIQUE (job_id, data_type)
);

CREATE INDEX idx_checkpoint_job ON sync_checkpoints(job_id);
CREATE INDEX idx_checkpoint_data_type ON sync_checkpoints(data_type);
