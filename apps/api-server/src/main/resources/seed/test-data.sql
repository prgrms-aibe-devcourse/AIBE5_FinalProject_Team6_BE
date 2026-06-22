-- Local dev seed data. Auto-runs on startup. Resets on app restart (ddl-auto: create-drop).
-- H2 in-memory (MODE=MySQL) 전용 — MySQL 전용 문법(NOW(6), INTERVAL 산술) 사용 불가
-- ============================================================
-- 테스트 계정
--   팬 로그인      : fan@fandrops.test  / Test1234!  (fan id=1, NOVA 팔로우)
--   팬 로그인2     : fan2@fandrops.test / Test1234!  (fan id=2, 팔로우 0명)
--   에이전시 로그인 : agency@fandrops.test / Test1234!
--   어드민 로그인  : admin@fandrops.com / Test1234!
--   아티스트 로그인 : artist / Test1234!  (NOVA 멤버 하늘, artist_member id=1)
--   아티스트 프로필 : NOVA(id=1) LUNA(id=2) ECHO(id=3)
--   그룹 멤버 수   : NOVA 4명 · LUNA 4명 · ECHO 1명 (이미지/수정본 프로필 기준, URL은 업로드로 채움)
--   팬 팔로우      : fan(id=1) -> NOVA(id=1) 초기 팔로우 상태
-- ============================================================

-- 0. ShedLock 테이블 (JPA 엔티티 아님 — Hibernate ddl-auto 대상 외)
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- 1. 에이전시 계정 (id=1 고정 -> JWT sub=1 role=AGENCY)
--    비밀번호: Test1234!  (BCrypt strength=10)
INSERT INTO agency_account (id, login_id, password_hash, company_name, contact_email, status, role, created_at)
VALUES (1, 'agency@fandrops.test',
        '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re',
        '테스트 기획사', 'agency@fandrops.test', 'ACTIVE', 'AGENCY', NOW());

-- 2. 팬 계정 (id=1 고정 -> JWT sub=1 -> X-Fan-Id: 1)
--    비밀번호: Test1234!  (BCrypt strength=10)
INSERT INTO fan (id, email, nickname, auth_provider, provider_id, password_hash, is_allow_notification, created_at)
VALUES (1, 'fan@fandrops.test', '테스트팬', 'LOCAL', 'local-fe-1',
        '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re',
        true, NOW());

-- 3. 아티스트 프로필 (id 고정: FE hardcode 1/2/3)
--    agency_id=1 -- 위에서 생성한 테스트 기획사 계정과 연결
INSERT INTO artist_profile (id, agency_id, name, fan_count, joined_at, bio)
VALUES
    (1, 1, 'NOVA', 0, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 NOVA'),
    (2, 1, 'LUNA', 0, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 LUNA'),
    (3, 1, 'ECHO', 0, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 ECHO');

-- 3-1. 아티스트 멤버 (finalize_assets.py 프로필 기준 인원·이름)
--    id=1 고정 -> JWT sub=1 role=ARTIST, login_id: artist / Test1234!
INSERT INTO artist_member (id, artist_id, login_id, password_hash, member_name, role)
VALUES
    (1, 1, 'artist',           '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '하늘', 'ARTIST'),
    (2, 1, 'seed-nova-sera',   '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '세라', 'ARTIST'),
    (3, 1, 'seed-nova-mina',   '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '미나', 'ARTIST'),
    (4, 1, 'seed-nova-yujin',  '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '유진', 'ARTIST'),
    (5, 2, 'seed-luna-eunbyeol', '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '은별', 'ARTIST'),
    (6, 2, 'seed-luna-dal',      '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '달',   'ARTIST'),
    (7, 2, 'seed-luna-haneul',   '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '하늘', 'ARTIST'),
    (8, 2, 'seed-luna-seoyeon',  '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '서연', 'ARTIST'),
    (9, 3, 'seed-echo',          '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', 'ECHO', 'ARTIST');

-- 4. 아티스트 피드
--    artist_member_id=1 = 위 NOVA 아티스트 멤버
--    H2: DATEADD('DAY', -N, NOW()) / DATEADD('HOUR', -N, NOW())

-- NOVA 피드 5개
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (1, 1, 'NOVA 팬 여러분, 안녕하세요! 드디어 컴백 준비가 시작됐어요.',  12,  3, DATEADD('DAY',  -5, NOW())),
    (1, 1, '오늘 뮤직비디오 촬영 완료! 기대해주세요.',                    27,  8, DATEADD('DAY',  -3, NOW())),
    (1, 1, '새 앨범 타이틀곡 작업 중. 힌트: 여름 느낌 물씬~',              9,  1, DATEADD('DAY',  -1, NOW())),
    (1, 1, '오늘 라이브 방송 22:00 KST 시작합니다! 기다려줘서 고마워요.', 45, 15, DATEADD('HOUR', -12, NOW())),
    (1, 1, '드롭스 굿즈 최종 디자인 확정됐어요. 곧 공개 예정.',             6,  0, DATEADD('HOUR',  -1, NOW()));

-- LUNA 피드 3개 (artist_member_id=5 은별)
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (2, 5, 'LUNA 1주년을 함께해줘서 정말 감사해요. 팬 여러분 최고!', 33, 11, DATEADD('DAY',  -4, NOW())),
    (2, 5, '포토카드 세트 패키지 디자인 비하인드 공개.',               18,  5, DATEADD('DAY',  -2, NOW())),
    (2, 5, '다음 주 팬미팅 일정 공지 드립니다. 꼭 확인해주세요!',       7,  2, DATEADD('HOUR',  -6, NOW()));

-- ECHO 피드 3개 (artist_member_id=9 ECHO)
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (3, 9, 'ECHO 솔로 데뷔 앨범 발매 D-7. Limited Vinyl 예약 오픈!', 51, 20, DATEADD('DAY',  -7, NOW())),
    (3, 9, '레코딩 스튜디오에서 열심히 작업 중. 곧 만나요.',           14,  4, DATEADD('DAY',  -2, NOW())),
    (3, 9, '오늘 인스타 라이브 예정! 솔로 앨범 수록곡 일부 공개됩니다.', 8,  1, DATEADD('HOUR',  -3, NOW()));

-- 4. 팬 팔로우 (fan_id=1 -> NOVA artist_id=1)
--    FE 초기 상태: NOVA는 이미 팔로우, LUNA/ECHO는 미팔로우
INSERT INTO user_follow (fan_id, artist_id, followed_at)
VALUES (1, 1, NOW());

UPDATE artist_profile SET fan_count = 1 WHERE id = 1;

-- 5. 테스트 상품 및 재고
INSERT INTO product (artist_id, name, price, status)
VALUES
    (1, 'NOVA 포토카드 세트',  15000, 'ON_SALE'),
    (1, 'NOVA 응원봉',         35000, 'ON_SALE'),
    (1, 'NOVA 한정판 포스터',  12000, 'SOLD_OUT'),
    (2, 'LUNA 1주년 포토북',   29000, 'ON_SALE'),
    (3, 'ECHO Limited Vinyl',  45000, 'ON_SALE');

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 100, 0, 100, 0
FROM product WHERE name IN ('NOVA 포토카드 세트', 'NOVA 응원봉', 'LUNA 1주년 포토북', 'ECHO Limited Vinyl');

-- 6. 아티스트 스케줄 (artist_id=1, NOVA)
--    scheduled_at: H2 DATEADD 사용 (NOW() 기준 상대값)
INSERT INTO artist_schedule (artist_id, notice_id, title, type, scheduled_at, live_url)
VALUES
    (1, null, 'NOVA 새 앨범 발매',             'DROP',   DATEADD('DAY',  7, NOW()), null),
    (1, null, 'NOVA 컴백 라이브 방송',          'LIVE',   DATEADD('DAY', 10, NOW()), 'https://live.example.com/nova'),
    (1, null, 'NOVA 팬미팅 — 서울 월드컵경기장', 'EVENT',  DATEADD('DAY', 21, NOW()), null),
    (1, null, '공식 팬클럽 멤버십 갱신 안내',    'NOTICE', DATEADD('DAY', -2, NOW()), null);

-- 7. 굿즈 투표 (artist_id=1, NOVA)
INSERT INTO goods_vote (id, artist_id, title, ends_at, is_active, created_at)
VALUES (1, 1, 'NOVA 컴백 굿즈 — 어떤 디자인이 좋아요?', DATEADD('DAY', 7, NOW()), true, NOW());

INSERT INTO goods_vote_option (vote_id, label, image_url, vote_count)
VALUES
    (1, '레드 에디션', null, 0),
    (1, '블루 에디션', null, 0);

-- 7. 출석 이벤트 (artist_id=1, NOVA) — 오늘 포함 1개월 범위
INSERT INTO attendance_event (artist_id, start_date, end_date, reward_desc, is_active, created_at)
VALUES (1, DATEADD('DAY', -14, CURRENT_DATE), DATEADD('DAY', 16, CURRENT_DATE),
        '매일 출석 체크하고 포인트를 모아보세요!', true, NOW());

-- 8. 알림 (fan_id=1)
INSERT INTO notification (fan_id, notification_type, target_id, message, is_read, sent_at)
VALUES
    (1, 'NEW_FEED',        1, 'NOVA가 새 피드를 올렸어요.',    false, DATEADD('HOUR', -2, NOW())),
    (1, 'ARTIST_SCHEDULE', 1, 'NOVA 컴백 일정이 등록됐어요.', false, DATEADD('HOUR', -1, NOW()));

-- 9. 주문 (fan_id=1) — PAID 2건, PENDING 1건 (취소 버튼 테스트용)
INSERT INTO orders (fan_id, idempotency_key, order_payment_key, status, total_amount, created_at, updated_at)
VALUES
    (1, 'ord00000-0000-0000-0000-000000000001', 'opk_seed_0000000000000000000000000001', 'COMPLETED',    30000.00, DATEADD('DAY',  -7, NOW()), DATEADD('DAY',  -7, NOW())),
    (1, 'ord00000-0000-0000-0000-000000000002', 'opk_seed_0000000000000000000000000002', 'COMPLETED',    29000.00, DATEADD('DAY',  -3, NOW()), DATEADD('DAY',  -3, NOW())),
    (1, 'ord00000-0000-0000-0000-000000000003', 'opk_seed_0000000000000000000000000003', 'RESERVED', 45000.00, DATEADD('HOUR', -2, NOW()), DATEADD('HOUR', -2, NOW()));

INSERT INTO order_item (order_id, product_id, quantity, price)
SELECT o.id, p.id, 2, 15000.00
FROM orders o, product p
WHERE o.idempotency_key = 'ord00000-0000-0000-0000-000000000001' AND p.name = 'NOVA 포토카드 세트';

INSERT INTO order_item (order_id, product_id, quantity, price)
SELECT o.id, p.id, 1, 29000.00
FROM orders o, product p
WHERE o.idempotency_key = 'ord00000-0000-0000-0000-000000000002' AND p.name = 'LUNA 1주년 포토북';

INSERT INTO order_item (order_id, product_id, quantity, price)
SELECT o.id, p.id, 1, 45000.00
FROM orders o, product p
WHERE o.idempotency_key = 'ord00000-0000-0000-0000-000000000003' AND p.name = 'ECHO Limited Vinyl';

-- RESERVED 주문(order 3)에 대한 재고 예약 수량 반영 — restore() 호출 시 InvalidInventoryStateException 방지
UPDATE inventory
SET reserved_qty = 1, available_qty = 99
WHERE product_id = (SELECT id FROM product WHERE name = 'ECHO Limited Vinyl');

-- ============================================================
-- [USER 도메인] 배너 · Admin · 입점 신청 · 알림 · 팬2 QA seed
-- ============================================================

-- A1. 배너 (MAIN) — FanApp HOME / GET /banners/main
--   B1-1: Admin MAIN 활성 (exposure_order=1) → 노출
--   B1-2: Admin MAIN 활성 (exposure_order=2) → 노출
--   B2:   Agency MAIN 활성 (agency_id=1)     → 노출
--   B3:   비활성 (is_active=false)           → API 미노출
--   B4:   기간 만료 (end_at < NOW())         → API 미노출
INSERT INTO banner (banner_type, agency_id, title, image_url, landing_url, exposure_order, is_active, start_at, end_at)
VALUES
    ('MAIN', NULL, '[Admin] NOVA 컴백 D-7 특별 프로모션',
     'https://placehold.co/1200x400/FF5C8D/FFFFFF?text=NOVA+Comeback',
     'https://fandrops.test/drops/nova', 1, true,
     DATEADD('DAY', -1, NOW()), DATEADD('DAY', 30, NOW())),

    ('MAIN', NULL, '[Admin] ECHO 한정판 Vinyl 오픈런 안내',
     'https://placehold.co/1200x400/5C8DFF/FFFFFF?text=ECHO+Limited+Vinyl',
     'https://fandrops.test/store/echo', 2, true,
     DATEADD('DAY', -3, NOW()), DATEADD('DAY', 14, NOW())),

    ('MAIN', 1, '[Agency] 테스트 기획사 여름 팝업 스토어',
     'https://placehold.co/1200x400/8DFF5C/333333?text=Agency+Popup',
     'https://fandrops.test/agency/1', 3, true,
     NOW(), DATEADD('DAY', 7, NOW())),

    ('MAIN', NULL, '[비활성] 종료된 이벤트 배너',
     'https://placehold.co/1200x400/CCCCCC/FFFFFF?text=Inactive+Banner',
     'https://fandrops.test/events', 4, false,
     DATEADD('DAY', -10, NOW()), DATEADD('DAY', 20, NOW())),

    ('MAIN', NULL, '[기간만료] 봄 시즌 특별전',
     'https://placehold.co/1200x400/FFD700/333333?text=Expired+Banner',
     'https://fandrops.test/spring', 5, true,
     DATEADD('DAY', -30, NOW()), DATEADD('DAY', -1, NOW()));

-- A2. Admin 패스워드를 Test1234! 로 통일
--     V18+V19 마이그레이션이 admin@fandrops.com 계정을 이미 생성하므로 UPDATE만 수행
UPDATE admin_account
SET password_hash = '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re'
WHERE login_id = 'admin@fandrops.com';

-- A3. 입점 신청 — Admin 심사 QA (PENDING · APPROVED · REJECTED 각 1건)
INSERT INTO agency_application
    (company_name, business_registration_number, representative_name,
     contact_email, contact_phone, introduction, target_artist_name,
     status, reject_reason, applied_at, reviewed_at)
VALUES
    ('신규 크리에이터 엔터',  '123-45-67890', '김신규',
     'newcreator@example.com', '010-1234-5678',
     '신인 걸그룹 BLOOM 소속사입니다. 팬덤 커뮤니티 구축을 목표로 합니다.',
     'BLOOM', 'PENDING', null,
     DATEADD('HOUR', -3, NOW()), null),

    ('글로벌 스타 엔터',      '234-56-78901', '이글로벌',
     'globalstar@example.com', '010-2345-6789',
     '중견 엔터테인먼트사로 다수의 아티스트를 보유하고 있습니다.',
     'PRISM', 'APPROVED', null,
     DATEADD('DAY', -10, NOW()), DATEADD('DAY', -8, NOW())),

    ('소규모 독립 기획사',    '345-67-89012', '박소규',
     'indie@example.com', '010-3456-7890',
     '소규모 인디 아티스트 지원 기획사입니다.',
     'INDIE_A', 'REJECTED', '제출 서류 미비 및 팬덤 규모 기준 미달',
     DATEADD('DAY', -20, NOW()), DATEADD('DAY', -18, NOW()));

-- A4. 알림 보강 — fan_id=1 다타입 QA (is_read mix)
--     기존: NEW_FEED, ARTIST_SCHEDULE
--     추가: RESTOCK, PAYMENT_SUCCESS, NEW_COMMENT
INSERT INTO notification (fan_id, notification_type, target_id, message, is_read, sent_at)
VALUES
    (1, 'RESTOCK',         1, 'NOVA 한정판 포스터가 재입고됐어요!',      true,  DATEADD('DAY',  -2, NOW())),
    (1, 'PAYMENT_SUCCESS', 1, 'ECHO Limited Vinyl 결제가 완료됐어요.',    false, DATEADD('DAY',  -3, NOW())),
    (1, 'NEW_COMMENT',     1, 'NOVA 피드에 내 댓글에 답글이 달렸어요.',   true,  DATEADD('HOUR', -5, NOW()));

-- A5. fan_id=2 — 팔로우 0명 (마이아티스트 추천 숨김 케이스 테스트)
INSERT INTO fan (id, email, nickname, auth_provider, provider_id, password_hash, is_allow_notification, created_at)
VALUES (2, 'fan2@fandrops.test', '테스트팬2', 'LOCAL', 'local-fe-2',
        '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re',
        true, NOW());

-- ============================================================
-- [ORDER 도메인] 드롭스 · 장바구니 · 주문/결제 · 재고이력 · 재입고알림 · STORE 배너 seed
-- ============================================================

-- B1. 기존 SOLD_OUT 상품 (NOVA 한정판 포스터)에 inventory 추가 — total=0
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 0, 0, 0, 0 FROM product WHERE name = 'NOVA 한정판 포스터';

-- B2. 저재고 regular 상품 추가 (available_qty=3)
INSERT INTO product (artist_id, name, price, status)
VALUES (1, 'NOVA 저재고 랜덤 키링', 8000, 'ON_SALE');

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 3, 0, 3, 0 FROM product WHERE name = 'NOVA 저재고 랜덤 키링';

-- B3. 드롭스 상품 4종 (D1~D4) + 각 inventory
-- D1: NOVA 진행중 (drops_start_at < NOW < drops_end_at, available=40)
INSERT INTO product (artist_id, name, price, status, drops_start_at, drops_end_at)
VALUES (1, 'NOVA 드롭스 한정 굿즈 세트', 59000, 'ON_SALE',
        DATEADD('DAY', -1, NOW()), DATEADD('DAY', 3, NOW()));

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 40, 0, 40, 0 FROM product WHERE name = 'NOVA 드롭스 한정 굿즈 세트';

-- D2: NOVA 진행중이지만 품절 (available=0, SOLD_OUT)
INSERT INTO product (artist_id, name, price, status, drops_start_at, drops_end_at)
VALUES (1, 'NOVA 드롭스 컴백 포토북 (품절)', 39000, 'SOLD_OUT',
        DATEADD('DAY', -2, NOW()), DATEADD('DAY', 2, NOW()));

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 0, 0, 0, 0 FROM product WHERE name = 'NOVA 드롭스 컴백 포토북 (품절)';

-- D3: LUNA 오픈 예정 (+2일, 카운트다운)
INSERT INTO product (artist_id, name, price, status, drops_start_at, drops_end_at)
VALUES (2, 'LUNA 드롭스 미니앨범 패키지', 49000, 'ON_SALE',
        DATEADD('DAY', 2, NOW()), DATEADD('DAY', 5, NOW()));

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 300, 0, 300, 0 FROM product WHERE name = 'LUNA 드롭스 미니앨범 패키지';

-- D4: 만료된 drops (drops_end_at < NOW → findDrops 결과에 미포함)
INSERT INTO product (artist_id, name, price, status, drops_start_at, drops_end_at)
VALUES (1, 'NOVA 스프링 드롭스 (만료)', 25000, 'SOLD_OUT',
        DATEADD('DAY', -14, NOW()), DATEADD('DAY', -1, NOW()));

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 0, 0, 0, 0 FROM product WHERE name = 'NOVA 스프링 드롭스 (만료)';

-- B4. 장바구니 — fan_id=1 (2품목), fan_id=2 (빈 카트)
INSERT INTO cart (fan_id)
VALUES (1), (2);

INSERT INTO cart_item (cart_id, product_id, quantity, added_at)
SELECT c.id, p.id, 2, NOW()
FROM cart c, product p
WHERE c.fan_id = 1 AND p.name = 'NOVA 포토카드 세트';

INSERT INTO cart_item (cart_id, product_id, quantity, added_at)
SELECT c.id, p.id, 1, NOW()
FROM cart c, product p
WHERE c.fan_id = 1 AND p.name = 'NOVA 드롭스 한정 굿즈 세트';

-- B5. 신규 주문 — COMPLETED × 2 (아티스트별), CANCELLED × 1
--   ord4: NOVA 드롭스 → payment SUCCESS
--   ord5: LUNA 포토북 → payment SUCCESS
--   ord6: NOVA 응원봉 → payment FAILED → CANCELLED (Saga 보상)
INSERT INTO orders (fan_id, idempotency_key, order_payment_key, status, total_amount, created_at, updated_at)
VALUES
    (1, 'ord00000-0000-0000-0000-000000000004', 'opk_seed_0000000000000000000000000004',
     'COMPLETED', 59000.00, DATEADD('DAY', -14, NOW()), DATEADD('DAY', -14, NOW())),
    (1, 'ord00000-0000-0000-0000-000000000005', 'opk_seed_0000000000000000000000000005',
     'COMPLETED', 29000.00, DATEADD('DAY',  -5, NOW()), DATEADD('DAY',  -5, NOW())),
    (1, 'ord00000-0000-0000-0000-000000000006', 'opk_seed_0000000000000000000000000006',
     'CANCELLED', 35000.00, DATEADD('DAY',  -4, NOW()), DATEADD('DAY',  -4, NOW()));

INSERT INTO order_item (order_id, product_id, quantity, price)
SELECT o.id, p.id, 1, 59000.00
FROM orders o, product p
WHERE o.idempotency_key = 'ord00000-0000-0000-0000-000000000004'
  AND p.name = 'NOVA 드롭스 한정 굿즈 세트';

INSERT INTO order_item (order_id, product_id, quantity, price)
SELECT o.id, p.id, 1, 29000.00
FROM orders o, product p
WHERE o.idempotency_key = 'ord00000-0000-0000-0000-000000000005'
  AND p.name = 'LUNA 1주년 포토북';

INSERT INTO order_item (order_id, product_id, quantity, price)
SELECT o.id, p.id, 1, 35000.00
FROM orders o, product p
WHERE o.idempotency_key = 'ord00000-0000-0000-0000-000000000006'
  AND p.name = 'NOVA 응원봉';

-- B6. 결제 행 연결
--   ord4: SUCCESS (카드)
--   ord5: SUCCESS (카드)
--   ord6: FAILED → CANCELLED 트리거 (Saga 보상 완료, payment_key = PG 반환값)
INSERT INTO payment (order_id, payment_key, amount, method, status, paid_at, failed_at, created_at, version)
SELECT o.id, 'toss_SEED_ORD4_CONFIRM_PAY_KEY_01', 59000, '카드', 'SUCCESS',
       DATEADD('DAY', -14, NOW()), null, DATEADD('DAY', -14, NOW()), 0
FROM orders o WHERE o.idempotency_key = 'ord00000-0000-0000-0000-000000000004';

INSERT INTO payment (order_id, payment_key, amount, method, status, paid_at, failed_at, created_at, version)
SELECT o.id, 'toss_SEED_ORD5_CONFIRM_PAY_KEY_01', 29000, '카드', 'SUCCESS',
       DATEADD('DAY', -5, NOW()), null, DATEADD('DAY', -5, NOW()), 0
FROM orders o WHERE o.idempotency_key = 'ord00000-0000-0000-0000-000000000005';

INSERT INTO payment (order_id, payment_key, amount, method, status, paid_at, failed_at, created_at, version)
SELECT o.id, 'toss_SEED_ORD6_FAILED_PAY_KEY_001', 35000, '카드', 'FAILED',
       null, DATEADD('DAY', -4, NOW()), DATEADD('DAY', -4, NOW()), 0
FROM orders o WHERE o.idempotency_key = 'ord00000-0000-0000-0000-000000000006';

-- B7. inventory_history — Agency 재고 이력 QA (RESERVE · DECREASE · RELEASE · COMPENSATE 포함)
-- NOVA 포토카드 세트 / order1 → RESERVE → DECREASE (완료)
INSERT INTO inventory_history (inventory_id, change_type, qty_delta, qty_before, qty_after, reference_id, ref_type)
SELECT inv.id, 'RESERVE', -2, 100, 98, o.id, 'ORDER'
FROM inventory inv, product p, orders o
WHERE inv.product_id = p.id AND p.name = 'NOVA 포토카드 세트'
  AND o.idempotency_key = 'ord00000-0000-0000-0000-000000000001';

INSERT INTO inventory_history (inventory_id, change_type, qty_delta, qty_before, qty_after, reference_id, ref_type)
SELECT inv.id, 'DECREASE', -2, 100, 98, o.id, 'ORDER'
FROM inventory inv, product p, orders o
WHERE inv.product_id = p.id AND p.name = 'NOVA 포토카드 세트'
  AND o.idempotency_key = 'ord00000-0000-0000-0000-000000000001';

-- LUNA 1주년 포토북 / order2 → RESERVE → DECREASE
INSERT INTO inventory_history (inventory_id, change_type, qty_delta, qty_before, qty_after, reference_id, ref_type)
SELECT inv.id, 'RESERVE', -1, 100, 99, o.id, 'ORDER'
FROM inventory inv, product p, orders o
WHERE inv.product_id = p.id AND p.name = 'LUNA 1주년 포토북'
  AND o.idempotency_key = 'ord00000-0000-0000-0000-000000000002';

INSERT INTO inventory_history (inventory_id, change_type, qty_delta, qty_before, qty_after, reference_id, ref_type)
SELECT inv.id, 'DECREASE', -1, 99, 98, o.id, 'ORDER'
FROM inventory inv, product p, orders o
WHERE inv.product_id = p.id AND p.name = 'LUNA 1주년 포토북'
  AND o.idempotency_key = 'ord00000-0000-0000-0000-000000000002';

-- NOVA 드롭스 한정 굿즈 / order4 → RESERVE → DECREASE
INSERT INTO inventory_history (inventory_id, change_type, qty_delta, qty_before, qty_after, reference_id, ref_type)
SELECT inv.id, 'RESERVE', -1, 41, 40, o.id, 'ORDER'
FROM inventory inv, product p, orders o
WHERE inv.product_id = p.id AND p.name = 'NOVA 드롭스 한정 굿즈 세트'
  AND o.idempotency_key = 'ord00000-0000-0000-0000-000000000004';

INSERT INTO inventory_history (inventory_id, change_type, qty_delta, qty_before, qty_after, reference_id, ref_type)
SELECT inv.id, 'DECREASE', -1, 40, 39, o.id, 'ORDER'
FROM inventory inv, product p, orders o
WHERE inv.product_id = p.id AND p.name = 'NOVA 드롭스 한정 굿즈 세트'
  AND o.idempotency_key = 'ord00000-0000-0000-0000-000000000004';

-- NOVA 응원봉 / order6 → RESERVE → RELEASE (취소 보상) + COMPENSATE
INSERT INTO inventory_history (inventory_id, change_type, qty_delta, qty_before, qty_after, reference_id, ref_type)
SELECT inv.id, 'RESERVE', -1, 100, 99, o.id, 'ORDER'
FROM inventory inv, product p, orders o
WHERE inv.product_id = p.id AND p.name = 'NOVA 응원봉'
  AND o.idempotency_key = 'ord00000-0000-0000-0000-000000000006';

INSERT INTO inventory_history (inventory_id, change_type, qty_delta, qty_before, qty_after, reference_id, ref_type)
SELECT inv.id, 'COMPENSATE', 1, 99, 100, o.id, 'ORDER'
FROM inventory inv, product p, orders o
WHERE inv.product_id = p.id AND p.name = 'NOVA 응원봉'
  AND o.idempotency_key = 'ord00000-0000-0000-0000-000000000006';

-- B8. 재입고 알림 — fan_id=1 / SOLD_OUT 상품 2건 PENDING
INSERT INTO restock_alert (fan_id, product_id, status)
SELECT 1, id, 'PENDING' FROM product WHERE name = 'NOVA 한정판 포스터';

INSERT INTO restock_alert (fan_id, product_id, status)
SELECT 1, id, 'PENDING' FROM product WHERE name = 'NOVA 드롭스 컴백 포토북 (품절)';

-- B9. STORE 배너 — 스토어 히어로 (활성 2건 + 비활성 1건)
--   S1: product_id 없이 landing_url만 (일반 기획전)
--   S2: ECHO Vinyl product_id 연결
--   S3: 비활성
INSERT INTO banner (banner_type, title, image_url, landing_url, exposure_order, is_active, start_at, end_at)
VALUES
    ('STORE', 'NOVA 드롭스 지금 구매하기',
     'https://placehold.co/1200x400/FF5C8D/FFFFFF?text=NOVA+Drops',
     'https://fandrops.test/store/nova-drops', 1, true,
     DATEADD('DAY', -1, NOW()), DATEADD('DAY', 3, NOW())),
    ('STORE', '[비활성] 구 스토어 기획전',
     'https://placehold.co/1200x400/CCCCCC/FFFFFF?text=Inactive+Store',
     'https://fandrops.test/store/archive', 3, false,
     DATEADD('DAY', -30, NOW()), DATEADD('DAY', 20, NOW()));

INSERT INTO banner (banner_type, title, image_url, landing_url, exposure_order, is_active, start_at, end_at, product_id)
SELECT 'STORE', 'ECHO Limited Vinyl 마지막 특가',
       'https://placehold.co/1200x400/5C8DFF/FFFFFF?text=ECHO+Vinyl+Sale',
       'https://fandrops.test/products/echo-vinyl', 2, true,
       DATEADD('DAY', -3, NOW()), DATEADD('DAY', 14, NOW()), id
FROM product WHERE name = 'ECHO Limited Vinyl';

-- ============================================================
-- [COMMUNITY 도메인] 아티스트멤버 · 피드좋아요 · 댓글 · 투표 · 출석 · 일정/공지 seed
-- ============================================================

-- C1. artist_member 확인
--     NOVA id=1(하늘, login=artist) · LUNA id=5(은별) · ECHO id=9

-- C2. feed_like — fan_id=1 이 NOVA 피드 2건 좋아요
--     CHECK: (fan_id IS NOT NULL) != (artist_member_id IS NOT NULL)
INSERT INTO feed_like (feed_id, fan_id, artist_member_id, artist_id, created_at)
SELECT id, 1, null, null, NOW()
FROM artist_feed
WHERE content LIKE 'NOVA 팬 여러분%' AND artist_id = 1;

INSERT INTO feed_like (feed_id, fan_id, artist_member_id, artist_id, created_at)
SELECT id, 1, null, null, DATEADD('MINUTE', 10, NOW())
FROM artist_feed
WHERE content LIKE '오늘 라이브 방송%' AND artist_id = 1;

-- like_count 동기화
UPDATE artist_feed SET like_count = like_count + 1
WHERE content LIKE 'NOVA 팬 여러분%' AND artist_id = 1;

UPDATE artist_feed SET like_count = like_count + 1
WHERE content LIKE '오늘 라이브 방송%' AND artist_id = 1;

-- C3. comment — fan1 최상위 댓글 2건, HANI 대댓글 1건
--     CHECK1: fan_id XOR artist_member_id 필수
--     CHECK2: artist_member_id IS NOT NULL → parent_id IS NOT NULL (아티스트는 대댓글만)
-- Fan 최상위 댓글 A (Feed 1 — NOVA 팬 여러분...)
INSERT INTO comment (feed_id, artist_id, fan_id, artist_member_id, parent_id, content, created_at)
SELECT id, 1, 1, null, null,
       '정말 기대됩니다! 컴백 카운트다운 중이에요', NOW()
FROM artist_feed
WHERE content LIKE 'NOVA 팬 여러분%' AND artist_id = 1;

-- Fan 최상위 댓글 B (Feed 4 — 오늘 라이브 방송...)
INSERT INTO comment (feed_id, artist_id, fan_id, artist_member_id, parent_id, content, created_at)
SELECT id, 1, 1, null, null,
       '오늘 방송 꼭 볼게요! 응원해요', DATEADD('MINUTE', 2, NOW())
FROM artist_feed
WHERE content LIKE '오늘 라이브 방송%' AND artist_id = 1;

-- NOVA 멤버 대댓글 (댓글 A에 달린 아티스트 답글 — artist_member_id=1, login='artist')
INSERT INTO comment (feed_id, artist_id, fan_id, artist_member_id, parent_id, content, created_at)
SELECT c.feed_id, c.artist_id, null, 1, c.id,
       '고마워요! 꼭 같이 즐겨요', DATEADD('MINUTE', 15, NOW())
FROM comment c
WHERE c.content = '정말 기대됩니다! 컴백 카운트다운 중이에요' AND c.fan_id = 1;

-- comment_count 동기화
UPDATE artist_feed SET comment_count = comment_count + 2
WHERE content LIKE 'NOVA 팬 여러분%' AND artist_id = 1;

UPDATE artist_feed SET comment_count = comment_count + 1
WHERE content LIKE '오늘 라이브 방송%' AND artist_id = 1;

-- C4. comment_like — fan_id=1 이 HANI 대댓글에 좋아요
INSERT INTO comment_like (comment_id, fan_id, created_at)
SELECT id, 1, DATEADD('MINUTE', 20, NOW())
FROM comment
WHERE content = '고마워요! 꼭 같이 즐겨요' AND artist_member_id = 1;

-- C5. goods_vote 보강
-- 기존 NOVA 투표(vote_id=1) option vote_count 업데이트
UPDATE goods_vote_option SET vote_count = 120 WHERE vote_id = 1 AND label = '레드 에디션';
UPDATE goods_vote_option SET vote_count = 80  WHERE vote_id = 1 AND label = '블루 에디션';

-- fan_id=1 NOVA 투표 완료 기록 (중복 투표 차단 QA: UNIQUE uq_vote_fan(vote_id, fan_id))
INSERT INTO goods_vote_record (vote_id, option_id, fan_id, voted_at)
SELECT 1, o.id, 1, NOW()
FROM goods_vote_option o WHERE o.vote_id = 1 AND o.label = '레드 에디션';

-- LUNA 진행 중 투표
INSERT INTO goods_vote (artist_id, title, ends_at, is_active, created_at)
VALUES (2, 'LUNA 미니앨범 굿즈 디자인 투표', DATEADD('DAY', 5, NOW()), true, NOW());

INSERT INTO goods_vote_option (vote_id, label, image_url, vote_count)
SELECT id, '앙코르 에디션', null, 45
FROM goods_vote WHERE title = 'LUNA 미니앨범 굿즈 디자인 투표';

INSERT INTO goods_vote_option (vote_id, label, image_url, vote_count)
SELECT id, '첫 만남 에디션', null, 32
FROM goods_vote WHERE title = 'LUNA 미니앨범 굿즈 디자인 투표';

-- NOVA 종료 투표 (ends_at < NOW, is_active=false → GET /goods-votes 미포함)
INSERT INTO goods_vote (artist_id, title, ends_at, is_active, created_at)
VALUES (1, 'NOVA 데뷔 기념 굿즈 투표 (종료)', DATEADD('DAY', -3, NOW()), false,
        DATEADD('DAY', -14, NOW()));

INSERT INTO goods_vote_option (vote_id, label, image_url, vote_count)
SELECT id, '블랙 에디션', null, 120
FROM goods_vote WHERE title = 'NOVA 데뷔 기념 굿즈 투표 (종료)';

INSERT INTO goods_vote_option (vote_id, label, image_url, vote_count)
SELECT id, '화이트 에디션', null, 80
FROM goods_vote WHERE title = 'NOVA 데뷔 기념 굿즈 투표 (종료)';

-- C6. attendance_log — fan_id=1 오늘 체크인 완료
--     기존 NOVA 출석 이벤트(artist_id=1)가 TODAY 포함 범위이므로 UNIQUE(event_id,fan_id,date) 안전
INSERT INTO attendance_log (event_id, fan_id, checked_date, created_at)
SELECT e.id, 1, CURRENT_DATE, NOW()
FROM attendance_event e WHERE e.artist_id = 1 AND e.is_active = true;

-- C7. artist_schedule 보강 — LUNA/ECHO 각 LIVE·EVENT·DROP + 과거 NOTICE 1건
--     (artist_notice 별도 테이블 없음: type='NOTICE' 행이 공지 탭에 노출됨)
INSERT INTO artist_schedule (artist_id, notice_id, title, type, scheduled_at, live_url, content, external_ticket_url)
VALUES
    (2, null, 'LUNA 팬미팅 온라인 라이브',          'LIVE',
     DATEADD('DAY', 5, NOW()),  'https://live.example.com/luna', null, null),
    (2, null, 'LUNA 드롭스 미니앨범 오픈',           'DROP',
     DATEADD('DAY', 2, NOW()),  null, null, null),
    (3, null, 'ECHO 단독 콘서트 — 서울',            'EVENT',
     DATEADD('DAY', 14, NOW()), null, null, 'https://ticket.example.com/echo-concert'),
    (3, null, 'ECHO 컴백 인사말',                   'NOTICE',
     DATEADD('DAY', -5, NOW()), null,
     'ECHO 팬 여러분 안녕하세요. 드디어 솔로 앨범 발매를 앞두고 인사드립니다.', null);

-- NOVA 공지 2건 추가 (공지 탭 non-empty QA)
INSERT INTO artist_schedule (artist_id, notice_id, title, type, scheduled_at, live_url, content, external_ticket_url)
VALUES
    (1, null, '[공지] NOVA 드롭스 일정 안내',       'NOTICE',
     DATEADD('DAY', -1, NOW()), null,
     '팬 여러분, 다음 주 드롭스 일정을 안내드립니다. 놓치지 마세요!', null),
    (1, null, '[공지] 팬클럽 1주년 기념 이벤트',    'NOTICE',
     DATEADD('HOUR', -12, NOW()), null,
     '팬클럽 1주년을 기념해 특별 이벤트를 진행합니다. 자세한 내용은 피드를 확인해 주세요.', null);

-- C8. feed_image (선택) — NOVA 첫 피드에 이미지 2장
INSERT INTO feed_image (feed_id, image_url, created_at)
SELECT id,
       'https://placehold.co/800x600/FF5C8D/FFFFFF?text=NOVA+Feed+Image+1',
       NOW()
FROM artist_feed WHERE content LIKE 'NOVA 팬 여러분%' AND artist_id = 1;

INSERT INTO feed_image (feed_id, image_url, created_at)
SELECT id,
       'https://placehold.co/800x600/FF9CC0/FFFFFF?text=NOVA+Feed+Image+2',
       DATEADD('SECOND', 1, NOW())
FROM artist_feed WHERE content LIKE 'NOVA 팬 여러분%' AND artist_id = 1;
