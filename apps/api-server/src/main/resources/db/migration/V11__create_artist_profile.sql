CREATE TABLE IF NOT EXISTS artist_profile (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    agency_id         BIGINT       NOT NULL,
    name              VARCHAR(255) NOT NULL,
    fan_count         BIGINT       NOT NULL DEFAULT 0,
    joined_at         DATETIME     NOT NULL,
    profile_image_url VARCHAR(500),
    cover_image_url   VARCHAR(500),
    bio               TEXT,
    homepage_url      VARCHAR(500),
    youtube_url       VARCHAR(500),
    instagram_url     VARCHAR(500),
    INDEX idx_artist_profile_agency (agency_id)
);
