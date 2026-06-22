CREATE TABLE product_image (
    id          BIGINT AUTO_INCREMENT NOT NULL,
    product_id  BIGINT        NOT NULL,
    image_url   VARCHAR(1000) NOT NULL,
    sort_order  TINYINT       NOT NULL DEFAULT 0,
    is_primary  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_product_image_product_id (product_id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
);
