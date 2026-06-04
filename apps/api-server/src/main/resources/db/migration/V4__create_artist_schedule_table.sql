-- F03-05: ARTIST_SCHEDULE 통합 일정 테이블 (ERD §8)
-- notice_id FK: ARTIST_NOTICE 자동 연동 일정 표시 (nullable)
CREATE TABLE IF NOT EXISTS artist_schedule (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    artist_id    BIGINT       NOT NULL,
    notice_id    BIGINT       NULL,
    title        VARCHAR(255) NOT NULL,
    type         VARCHAR(20)  NOT NULL COMMENT 'DROP | LIVE | EVENT | NOTICE',
    scheduled_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_artist_schedule_artist_date (artist_id, scheduled_at)
);