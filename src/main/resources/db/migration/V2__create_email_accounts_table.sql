-- V2: Create email_accounts table
CREATE TABLE email_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    provider VARCHAR(20) NOT NULL,
    access_token_encrypted TEXT,
    refresh_token_encrypted TEXT,
    token_expires_at TIMESTAMP,
    granted_scopes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_synced_at TIMESTAMP,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_email_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_email_account_user ON email_accounts(user_id);
CREATE INDEX idx_email_account_email ON email_accounts(email);
CREATE INDEX idx_email_account_provider ON email_accounts(provider);
CREATE INDEX idx_email_account_status ON email_accounts(status);
