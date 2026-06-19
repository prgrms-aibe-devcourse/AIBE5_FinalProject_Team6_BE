-- ============================================================
-- 로컬 개발용 테스트 상품 시드 데이터
-- 실행: mysql -u <user> -p <database> < docs/seed/test-products.sql
-- 주의: 운영 DB에 절대 실행하지 말 것
-- ============================================================

-- 1. 테스트 아티스트 프로필 (agency_id는 FK 제약 없으므로 임의값 사용)
INSERT INTO artist_profile (agency_id, name, fan_count, joined_at, bio)
VALUES (999, '[테스트] Starlight', 0, '2024-01-01 00:00:00', '로컬 개발 테스트용 아티스트');

SET @artist_id = LAST_INSERT_ID();

-- 2. 테스트 상품 2개
INSERT INTO product (artist_id, name, price, status)
VALUES
    (@artist_id, '[테스트] Starlight 포토카드 세트', 15000.00, 'ON_SALE'),
    (@artist_id, '[테스트] Starlight 응원봉',        35000.00, 'ON_SALE');

-- 3. 각 상품의 재고 (total 100, reserved 0, available 100)
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 100, 0, 100, 0
FROM product
WHERE name LIKE '[테스트]%';

-- 확인용 조회
SELECT p.id AS product_id, p.name, p.price, p.status,
       i.total_qty, i.available_qty
FROM product p
JOIN inventory i ON i.product_id = p.id
WHERE p.name LIKE '[테스트]%';