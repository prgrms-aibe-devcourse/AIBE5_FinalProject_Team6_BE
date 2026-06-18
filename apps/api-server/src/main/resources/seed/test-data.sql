-- Local dev seed data. Auto-runs on startup. Resets on app restart (ddl-auto: create-drop).
-- H2 in-memory (MODE=MySQL) 전용 — MySQL 전용 문법(NOW(6), INTERVAL 산술) 사용 불가
-- ============================================================
-- 테스트 계정
--   팬 로그인      : fan@fandrops.test / Test1234!
--   에이전시 로그인 : agency@fandrops.test / Test1234!
--   아티스트 로그인 : hani / Test1234!
--   아티스트 프로필 : NOVA(id=1) LUNA(id=2) ECHO(id=3)
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

-- 3. 아티스트 피드
--    artist_member_id=1 = 팬(id=1)이 아티스트 멤버 역할로 작성
--    H2: DATEADD('DAY', -N, NOW()) / DATEADD('HOUR', -N, NOW())

-- NOVA 피드 5개
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (1, 1, 'NOVA 팬 여러분, 안녕하세요! 드디어 컴백 준비가 시작됐어요.',  12,  3, DATEADD('DAY',  -5, NOW())),
    (1, 1, '오늘 뮤직비디오 촬영 완료! 기대해주세요.',                    27,  8, DATEADD('DAY',  -3, NOW())),
    (1, 1, '새 앨범 타이틀곡 작업 중. 힌트: 여름 느낌 물씬~',              9,  1, DATEADD('DAY',  -1, NOW())),
    (1, 1, '오늘 라이브 방송 22:00 KST 시작합니다! 기다려줘서 고마워요.', 45, 15, DATEADD('HOUR', -12, NOW())),
    (1, 1, '드롭스 굿즈 최종 디자인 확정됐어요. 곧 공개 예정.',             6,  0, DATEADD('HOUR',  -1, NOW()));

-- LUNA 피드 3개
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (2, 1, 'LUNA 1주년을 함께해줘서 정말 감사해요. 팬 여러분 최고!', 33, 11, DATEADD('DAY',  -4, NOW())),
    (2, 1, '포토카드 세트 패키지 디자인 비하인드 공개.',               18,  5, DATEADD('DAY',  -2, NOW())),
    (2, 1, '다음 주 팬미팅 일정 공지 드립니다. 꼭 확인해주세요!',       7,  2, DATEADD('HOUR',  -6, NOW()));

-- ECHO 피드 3개
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (3, 1, 'ECHO 솔로 데뷔 앨범 발매 D-7. Limited Vinyl 예약 오픈!', 51, 20, DATEADD('DAY',  -7, NOW())),
    (3, 1, '레코딩 스튜디오에서 열심히 작업 중. 곧 만나요.',           14,  4, DATEADD('DAY',  -2, NOW())),
    (3, 1, '오늘 인스타 라이브 예정! 솔로 앨범 수록곡 일부 공개됩니다.', 8,  1, DATEADD('HOUR',  -3, NOW()));

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
