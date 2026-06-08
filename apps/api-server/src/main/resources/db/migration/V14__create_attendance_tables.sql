CREATE TABLE IF NOT EXISTS attendance_event (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    artist_id   BIGINT       NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    reward_desc VARCHAR(255),
    is_active   TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL
);

CREATE TABLE IF NOT EXISTS attendance_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id     BIGINT NOT NULL,
    fan_id       BIGINT NOT NULL,
    checked_date DATE   NOT NULL,
    created_at   DATETIME NOT NULL,
    UNIQUE INDEX uq_attendance_log (event_id, fan_id, checked_date),
    INDEX idx_attendance_log_event_fan (event_id, fan_id)
);