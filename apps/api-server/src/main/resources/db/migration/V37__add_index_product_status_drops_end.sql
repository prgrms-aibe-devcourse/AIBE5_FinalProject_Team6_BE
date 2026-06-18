-- 드롭스 만료 스케줄러 쿼리 최적화: status = 'ON_SALE' AND drops_end_at < :now
ALTER TABLE product
    ADD INDEX idx_product_status_drops_end (status, drops_end_at);
