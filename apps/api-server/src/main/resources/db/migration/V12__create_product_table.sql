CREATE TABLE IF NOT EXISTS product (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    artist_id  BIGINT         NOT NULL,
    name       VARCHAR(200)   NOT NULL,
    price      DECIMAL(15, 2) NOT NULL,
    status     VARCHAR(20)    NOT NULL DEFAULT 'ON_SALE',
    created_at DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_product_artist_id (artist_id)
);
