CREATE TABLE IF NOT EXISTS notification
(
    notification_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    fan_id            BIGINT       NOT NULL,
    notification_type VARCHAR(50)  NOT NULL,
    target_id         BIGINT,
    message           VARCHAR(500) NOT NULL,
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
    sent_at           DATETIME(6)  NOT NULL,
    INDEX idx_notification_fan_id (fan_id),
    INDEX idx_notification_fan_unread (fan_id, is_read)
);

CREATE TABLE IF NOT EXISTS notification_outbox_events
(
    outbox_event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type      VARCHAR(50) NOT NULL,
    resource_id     BIGINT,
    payload         JSON        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     INT         NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL,
    published_at    DATETIME(6),
    INDEX idx_notification_outbox_status (status)
);
