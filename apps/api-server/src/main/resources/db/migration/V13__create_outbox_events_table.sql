CREATE TABLE IF NOT EXISTS outbox_events
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    event_type     VARCHAR(50)  NOT NULL COMMENT 'NEW_FEED | NEW_COMMENT | ARTIST_SCHEDULE | RESTOCK',
    aggregate_type VARCHAR(50)  NOT NULL COMMENT 'ARTIST_FEED | COMMENT | ARTIST_SCHEDULE | PRODUCT',
    aggregate_id   BIGINT       NOT NULL,
    payload        JSON         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | PUBLISHED | FAILED',
    retry_count    INT          NOT NULL DEFAULT 0,
    published_at   DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_status_created (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;