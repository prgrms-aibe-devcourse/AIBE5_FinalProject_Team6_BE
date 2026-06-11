CREATE TABLE IF NOT EXISTS audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    occurred_at   DATETIME(6)  NOT NULL,
    actor_type    VARCHAR(20)  NOT NULL,
    actor_id      BIGINT       NULL,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50)  NOT NULL,
    resource_id   BIGINT       NULL,
    trace_id      VARCHAR(100) NULL,
    before_json   TEXT         NULL,
    after_json    TEXT         NULL,
    reason        VARCHAR(500) NULL,
    client_ip     VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    INDEX idx_audit_logs_actor (actor_type, actor_id),
    INDEX idx_audit_logs_resource (resource_type, resource_id),
    INDEX idx_audit_logs_occurred_at (occurred_at)
);
