CREATE TABLE IF NOT EXISTS agency_application (
    id                           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    company_name                 VARCHAR(255) NOT NULL,
    business_registration_number VARCHAR(100),
    representative_name          VARCHAR(100) NOT NULL,
    contact_email                VARCHAR(255) NOT NULL,
    contact_phone                VARCHAR(50)  NOT NULL,
    introduction                 TEXT         NOT NULL,
    target_artist_name           VARCHAR(255) NOT NULL,
    status                       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reject_reason                TEXT,
    applied_at                   DATETIME     NOT NULL,
    reviewed_at                  DATETIME,
    INDEX idx_agency_application_status (status)
);

CREATE TABLE IF NOT EXISTS agency_account (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    login_id         VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    company_name     VARCHAR(255) NOT NULL,
    contact_email    VARCHAR(255) NOT NULL,
    invitation_token VARCHAR(255),
    token_expired_at DATETIME,
    created_at       DATETIME     NOT NULL,
    UNIQUE KEY uq_agency_account_login_id (login_id)
);
