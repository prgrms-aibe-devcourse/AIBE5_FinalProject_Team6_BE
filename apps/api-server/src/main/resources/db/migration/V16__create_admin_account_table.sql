CREATE TABLE IF NOT EXISTS admin_account (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    login_id      VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME     NOT NULL,
    UNIQUE KEY uq_admin_account_login_id (login_id)
);