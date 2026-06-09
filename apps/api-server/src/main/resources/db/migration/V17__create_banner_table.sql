CREATE TABLE IF NOT EXISTS banner (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    banner_type    VARCHAR(20)  NOT NULL,
    title          VARCHAR(255) NOT NULL,
    image_url      VARCHAR(500) NOT NULL,
    landing_url    VARCHAR(500) NOT NULL,
    exposure_order INT          NOT NULL DEFAULT 0,
    is_active      TINYINT(1)   NOT NULL DEFAULT 1,
    start_at       DATETIME,
    end_at         DATETIME,
    INDEX idx_banner_type_active (banner_type, is_active)
);