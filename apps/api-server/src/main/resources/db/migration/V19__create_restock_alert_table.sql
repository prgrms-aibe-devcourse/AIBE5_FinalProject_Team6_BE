CREATE TABLE IF NOT EXISTS restock_alert (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    fan_id     BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_restock_alert_product_status (product_id, status),
    INDEX idx_restock_alert_fan_product (fan_id, product_id)
);
