CREATE TABLE IF NOT EXISTS cart (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    fan_id     BIGINT   NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_cart_fan_id (fan_id)
);

CREATE TABLE IF NOT EXISTS cart_item (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    cart_id    BIGINT   NOT NULL,
    product_id BIGINT   NOT NULL,
    quantity   INT      NOT NULL,
    added_at   DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_cart_item_cart_product (cart_id, product_id),
    INDEX idx_cart_item_cart_id (cart_id)
);
