CREATE TABLE IF NOT EXISTS inventory (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    product_id    BIGINT       NOT NULL,
    total_qty     INT          NOT NULL DEFAULT 0,
    reserved_qty  INT          NOT NULL DEFAULT 0,
    available_qty INT          NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE INDEX uk_inventory_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS inventory_history (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    inventory_id  BIGINT       NOT NULL,
    change_type   VARCHAR(20)  NOT NULL,
    qty_delta     INT          NOT NULL,
    qty_before    INT          NOT NULL,
    qty_after     INT          NOT NULL,
    reference_id  BIGINT       NOT NULL,
    ref_type      VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE INDEX uk_inventory_history_idempotency (inventory_id, reference_id, ref_type, change_type),
    INDEX idx_inventory_history_inventory_id (inventory_id)
);
