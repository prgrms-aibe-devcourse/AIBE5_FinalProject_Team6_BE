ALTER TABLE product
    ADD INDEX idx_product_regular_cursor (drops_start_at, id);
