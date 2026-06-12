CREATE TABLE IF NOT EXISTS artist_member (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    artist_id         BIGINT       NOT NULL,
    login_id          VARCHAR(100) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    member_name       VARCHAR(100) NOT NULL,
    role              VARCHAR(20)  NOT NULL DEFAULT 'ARTIST',
    profile_image_url VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX uq_artist_member_login_id (login_id),
    INDEX idx_artist_member_artist_id (artist_id)
);
