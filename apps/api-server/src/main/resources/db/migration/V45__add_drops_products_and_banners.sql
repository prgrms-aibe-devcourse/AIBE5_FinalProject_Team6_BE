-- V45: 드롭스 신규 상품 3종 추가 + 스토어 배너 정리
-- 1) 신규 드롭스 상품 (크리에이터 · 버튜버 · 아이돌)
-- 2) NOVA 드롭스 배너 → product_id 연결
-- 3) ECHO Limit 마지막 특가 배너 → 비활성화

-- ============================================================
-- 신규 드롭스 상품
-- ============================================================

-- E1: 소영 × Drops [GLOW UP] (크리에이터, artist_id=9)
INSERT INTO product (artist_id, name, price, status, drops_start_at, drops_end_at)
VALUES (9, '소영 × Drops: [GLOW UP] 뷰티 한정 컬렉션', 52000, 'ON_SALE',
        DATE_ADD(NOW(), INTERVAL -12 HOUR), DATE_ADD(NOW(), INTERVAL 4 DAY));

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 60, 0, 60, 0 FROM product WHERE name = '소영 × Drops: [GLOW UP] 뷰티 한정 컬렉션';

INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/FFAAA6/333333?text=GLOW+UP+DROP', 0, true
FROM product WHERE name = '소영 × Drops: [GLOW UP] 뷰티 한정 컬렉션';

-- E2: 리아 × Drops [FAIRY SIGNAL] (버튜버, artist_id=12)
INSERT INTO product (artist_id, name, price, status, drops_start_at, drops_end_at)
VALUES (12, '리아 × Drops: [FAIRY SIGNAL] 첫 EP 기념 패키지', 64000, 'ON_SALE',
        DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY));

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 120, 0, 120, 0 FROM product WHERE name = '리아 × Drops: [FAIRY SIGNAL] 첫 EP 기념 패키지';

INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/957DAD/FFFFFF?text=FAIRY+SIGNAL', 0, true
FROM product WHERE name = '리아 × Drops: [FAIRY SIGNAL] 첫 EP 기념 패키지';

-- E3: VORTEX × Drops [BLACKOUT] (아이돌, artist_id=7)
INSERT INTO product (artist_id, name, price, status, drops_start_at, drops_end_at)
VALUES (7, 'VORTEX × Drops: [BLACKOUT] 스트릿 캡슐 컬렉션', 79000, 'ON_SALE',
        DATE_ADD(NOW(), INTERVAL -6 HOUR), DATE_ADD(NOW(), INTERVAL 2 DAY));

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 30, 0, 30, 0 FROM product WHERE name = 'VORTEX × Drops: [BLACKOUT] 스트릿 캡슐 컬렉션';

INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/1a1a2e/FFFFFF?text=BLACKOUT', 0, true
FROM product WHERE name = 'VORTEX × Drops: [BLACKOUT] 스트릿 캡슐 컬렉션';

-- ============================================================
-- 신규 드롭스 STORE 배너 (각 아티스트 담당 에이전시)
-- ============================================================

INSERT INTO banner (banner_type, agency_id, title, image_url, landing_url, exposure_order, is_active, start_at, end_at, product_id)
SELECT 'STORE', 4, '소영 × Drops — GLOW UP 뷰티 한정 컬렉션',
       'https://placehold.co/1200x400/FFAAA6/333333?text=Soyoung+GLOW+UP+Drops',
       'https://fandrops.test/store', 4, true,
       DATE_ADD(NOW(), INTERVAL -12 HOUR), DATE_ADD(NOW(), INTERVAL 4 DAY), id
FROM product WHERE name = '소영 × Drops: [GLOW UP] 뷰티 한정 컬렉션';

INSERT INTO banner (banner_type, agency_id, title, image_url, landing_url, exposure_order, is_active, start_at, end_at, product_id)
SELECT 'STORE', 5, '리아 × Drops — FAIRY SIGNAL 오픈 예정',
       'https://placehold.co/1200x400/957DAD/FFFFFF?text=Lia+FAIRY+SIGNAL+Drops',
       'https://fandrops.test/store', 5, true,
       DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), id
FROM product WHERE name = '리아 × Drops: [FAIRY SIGNAL] 첫 EP 기념 패키지';

INSERT INTO banner (banner_type, agency_id, title, image_url, landing_url, exposure_order, is_active, start_at, end_at, product_id)
SELECT 'STORE', 3, 'VORTEX × Drops — BLACKOUT 스트릿 캡슐',
       'https://placehold.co/1200x400/1a1a2e/FFFFFF?text=VORTEX+BLACKOUT+Drops',
       'https://fandrops.test/store', 6, true,
       DATE_ADD(NOW(), INTERVAL -6 HOUR), DATE_ADD(NOW(), INTERVAL 2 DAY), id
FROM product WHERE name = 'VORTEX × Drops: [BLACKOUT] 스트릿 캡슐 컬렉션';

-- ============================================================
-- 기존 배너 정리
-- ============================================================

-- NOVA 드롭스 배너 → product_id 연결 (클릭 시 상품 상세 진입)
UPDATE banner b
    JOIN product p ON p.name = 'NOVA 드롭스 한정 굿즈 세트'
SET b.product_id = p.id
WHERE b.title = 'NOVA 드롭스 지금 구매하기'
  AND b.banner_type = 'STORE';

-- ECHO Limit 마지막 특가 배너 → 비활성화
UPDATE banner
SET is_active = false
WHERE title = 'ECHO Limit 마지막 특가'
  AND banner_type = 'STORE';