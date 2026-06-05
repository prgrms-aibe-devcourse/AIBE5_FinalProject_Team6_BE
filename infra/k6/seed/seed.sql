-- ============================================================
-- k6 부하 테스트용 seed 데이터
-- 실행: MySQL에 접속 후 이 파일 전체를 실행
--   docker exec -i fandrops_mysql mysql -u fandrops -pchange-me fandrops < infra/k6/seed/seed.sql
-- ============================================================

-- 기존 테스트 데이터 초기화 (재실행 가능하도록)
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM order_item   WHERE id <= 50;
DELETE FROM orders       WHERE id <= 50;
DELETE FROM inventory    WHERE product_id = 1;
DELETE FROM artist_feed  WHERE artist_id = 1;
DELETE FROM user_follow  WHERE artist_id = 1;
DELETE FROM fan          WHERE id <= 2100;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 1. fan (팬 계정)
--    - 시나리오 01/02/03/04: fan id=1
--    - 시나리오 05 (SSE 한계): fan id=1~2100
-- ============================================================
INSERT INTO fan (id, email, nickname, auth_provider, provider_id, password_hash, is_allow_notification, created_at)
VALUES (1, 'fan1@test.com', '테스트팬1', 'LOCAL', 'local-1', NULL, true, NOW());

-- fan id 2~2100 (시나리오 05용 — 한 번에 삽입)
INSERT INTO fan (id, email, nickname, auth_provider, provider_id, is_allow_notification, created_at)
WITH RECURSIVE seq AS (
    SELECT 2 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 2100
)
SELECT
    n,
    CONCAT('fan', n, '@test.com'),
    CONCAT('테스트팬', n),
    'LOCAL',
    CONCAT('local-', n),
    true,
    NOW()
FROM seq;

-- ============================================================
-- 2. inventory (재고)
--    - 시나리오 01/04: product_id=1, 재고 100개
-- ============================================================
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version, updated_at)
VALUES (1, 100, 0, 100, 0, NOW(6));

-- ============================================================
-- 3. artist_feed (아티스트 피드)
--    - 시나리오 02: artist_id=1 피드 20개
-- ============================================================
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (1, 1, '안녕하세요! 첫 번째 피드입니다 🎵',  0, 0, NOW(6)),
    (1, 1, '오늘 공연 준비 중입니다!',           0, 0, NOW(6)),
    (1, 1, '새 앨범 작업 중이에요 🎶',            0, 0, NOW(6)),
    (1, 1, '팬 여러분 감사합니다 💖',             0, 0, NOW(6)),
    (1, 1, '연습실에서 인사드려요',               0, 0, NOW(6)),
    (1, 1, '드롭스 이벤트 준비 완료!',            0, 0, NOW(6)),
    (1, 1, '오늘 날씨가 너무 좋아요 ☀️',          0, 0, NOW(6)),
    (1, 1, '신상품 곧 공개됩니다 👀',             0, 0, NOW(6)),
    (1, 1, '여러분이 보고 싶어요!',               0, 0, NOW(6)),
    (1, 1, '이번 주 방송 기대해주세요 📺',         0, 0, NOW(6)),
    (1, 1, '굿즈 디자인 확정됐어요 🎁',           0, 0, NOW(6)),
    (1, 1, '팬미팅 공지 곧 올게요!',              0, 0, NOW(6)),
    (1, 1, '오늘 연습 끝! 수고했어요 💪',          0, 0, NOW(6)),
    (1, 1, '새벽 스튜디오 작업 중 🌙',            0, 0, NOW(6)),
    (1, 1, '드롭스 기대해주세요!',                0, 0, NOW(6)),
    (1, 1, '오늘의 셀카 📸',                     0, 0, NOW(6)),
    (1, 1, '콘서트 리허설 완료!',                 0, 0, NOW(6)),
    (1, 1, '음반 작업 막바지예요 🎤',             0, 0, NOW(6)),
    (1, 1, '여러분 덕분에 힘이 나요 🙏',           0, 0, NOW(6)),
    (1, 1, '다음 드롭 날짜 공개 예정!',           0, 0, NOW(6));

-- ============================================================
-- 4. user_follow (팬 팔로우)
--    - 시나리오 02: fan_id=1 이 artist_id=1 팔로우
-- ============================================================
INSERT INTO user_follow (fan_id, artist_id, followed_at)
VALUES (1, 1, NOW(6));

-- ============================================================
-- 5. orders + order_item (RESERVED 상태 주문 50건)
--    - 시나리오 03: 결제 확인 테스트용
--    - fan_id=1~50, product_id=1, amount=15000 (StubProductPrice=10000 이지만 테스트 픽스처는 15000)
-- ============================================================
INSERT INTO orders (id, fan_id, idempotency_key, order_payment_key, status, total_amount, created_at, updated_at)
VALUES
    ( 1,  1, UUID(), CONCAT('opk-load-', LPAD( 1,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    ( 2,  2, UUID(), CONCAT('opk-load-', LPAD( 2,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    ( 3,  3, UUID(), CONCAT('opk-load-', LPAD( 3,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    ( 4,  4, UUID(), CONCAT('opk-load-', LPAD( 4,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    ( 5,  5, UUID(), CONCAT('opk-load-', LPAD( 5,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    ( 6,  6, UUID(), CONCAT('opk-load-', LPAD( 6,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    ( 7,  7, UUID(), CONCAT('opk-load-', LPAD( 7,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    ( 8,  8, UUID(), CONCAT('opk-load-', LPAD( 8,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    ( 9,  9, UUID(), CONCAT('opk-load-', LPAD( 9,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (10, 10, UUID(), CONCAT('opk-load-', LPAD(10,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (11, 11, UUID(), CONCAT('opk-load-', LPAD(11,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (12, 12, UUID(), CONCAT('opk-load-', LPAD(12,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (13, 13, UUID(), CONCAT('opk-load-', LPAD(13,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (14, 14, UUID(), CONCAT('opk-load-', LPAD(14,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (15, 15, UUID(), CONCAT('opk-load-', LPAD(15,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (16, 16, UUID(), CONCAT('opk-load-', LPAD(16,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (17, 17, UUID(), CONCAT('opk-load-', LPAD(17,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (18, 18, UUID(), CONCAT('opk-load-', LPAD(18,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (19, 19, UUID(), CONCAT('opk-load-', LPAD(19,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (20, 20, UUID(), CONCAT('opk-load-', LPAD(20,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (21, 21, UUID(), CONCAT('opk-load-', LPAD(21,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (22, 22, UUID(), CONCAT('opk-load-', LPAD(22,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (23, 23, UUID(), CONCAT('opk-load-', LPAD(23,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (24, 24, UUID(), CONCAT('opk-load-', LPAD(24,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (25, 25, UUID(), CONCAT('opk-load-', LPAD(25,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (26, 26, UUID(), CONCAT('opk-load-', LPAD(26,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (27, 27, UUID(), CONCAT('opk-load-', LPAD(27,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (28, 28, UUID(), CONCAT('opk-load-', LPAD(28,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (29, 29, UUID(), CONCAT('opk-load-', LPAD(29,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (30, 30, UUID(), CONCAT('opk-load-', LPAD(30,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (31, 31, UUID(), CONCAT('opk-load-', LPAD(31,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (32, 32, UUID(), CONCAT('opk-load-', LPAD(32,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (33, 33, UUID(), CONCAT('opk-load-', LPAD(33,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (34, 34, UUID(), CONCAT('opk-load-', LPAD(34,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (35, 35, UUID(), CONCAT('opk-load-', LPAD(35,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (36, 36, UUID(), CONCAT('opk-load-', LPAD(36,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (37, 37, UUID(), CONCAT('opk-load-', LPAD(37,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (38, 38, UUID(), CONCAT('opk-load-', LPAD(38,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (39, 39, UUID(), CONCAT('opk-load-', LPAD(39,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (40, 40, UUID(), CONCAT('opk-load-', LPAD(40,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (41, 41, UUID(), CONCAT('opk-load-', LPAD(41,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (42, 42, UUID(), CONCAT('opk-load-', LPAD(42,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (43, 43, UUID(), CONCAT('opk-load-', LPAD(43,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (44, 44, UUID(), CONCAT('opk-load-', LPAD(44,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (45, 45, UUID(), CONCAT('opk-load-', LPAD(45,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (46, 46, UUID(), CONCAT('opk-load-', LPAD(46,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (47, 47, UUID(), CONCAT('opk-load-', LPAD(47,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (48, 48, UUID(), CONCAT('opk-load-', LPAD(48,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (49, 49, UUID(), CONCAT('opk-load-', LPAD(49,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6)),
    (50, 50, UUID(), CONCAT('opk-load-', LPAD(50,4,'0')), 'RESERVED', 15000.00, NOW(6), NOW(6));

INSERT INTO order_item (order_id, product_id, quantity, price)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 50
)
SELECT n, 1, 1, 15000.00 FROM seq;

-- ============================================================
-- 확인용 쿼리
-- ============================================================
SELECT 'fan 수'        AS 항목, COUNT(*) AS 값 FROM fan          WHERE id <= 2100
UNION ALL
SELECT 'inventory'    AS 항목, COUNT(*) AS 값 FROM inventory     WHERE product_id = 1
UNION ALL
SELECT 'artist_feed'  AS 항목, COUNT(*) AS 값 FROM artist_feed   WHERE artist_id = 1
UNION ALL
SELECT 'user_follow'  AS 항목, COUNT(*) AS 값 FROM user_follow   WHERE artist_id = 1
UNION ALL
SELECT 'orders'       AS 항목, COUNT(*) AS 값 FROM orders        WHERE id <= 50
UNION ALL
SELECT 'order_item'   AS 항목, COUNT(*) AS 값 FROM order_item    WHERE order_id <= 50;