ALTER TABLE artist_schedule
    ADD COLUMN content TEXT NULL COMMENT 'NOTICE 타입 공지 본문';

CREATE TABLE IF NOT EXISTS artist_schedule_image (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT        NOT NULL,
    image_url   VARCHAR(2048) NOT NULL,
    sort_order  INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_artist_schedule_image_schedule (schedule_id)
);
