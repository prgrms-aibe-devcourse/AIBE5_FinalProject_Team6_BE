-- product 테이블에 드롭스 기간 컬럼 추가 (F04-02)
-- NULL = 상시 상품, 값 있음 = 드롭스 상품
ALTER TABLE product
    ADD COLUMN drops_start_at DATETIME(6) NULL AFTER status,
    ADD COLUMN drops_end_at   DATETIME(6) NULL AFTER drops_start_at,
    ADD INDEX idx_product_drops (drops_start_at, drops_end_at);
