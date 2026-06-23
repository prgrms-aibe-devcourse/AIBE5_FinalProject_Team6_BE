-- ============================================================
-- FE Phase 2 통합 검증용 테스트 시드 데이터
-- 실행: docker exec -i fandrops_mysql mysql -u fandrops -pchange-me fandrops < docs/seed/test-fe-data.sql
--      또는: mysql -u <user> -p <database> < docs/seed/test-fe-data.sql
-- 주의: 운영·스테이징 DB에 절대 실행하지 말 것
-- 재실행 안전: ON DUPLICATE KEY UPDATE / INSERT IGNORE 로 멱등 처리
-- ============================================================
-- 테스트 계정 요약
--   팬 로그인  : fan@fandrops.test  / Test1234!
--   아티스트   : NOVA(id=1) · LUNA(id=2) · ECHO(id=3)  agency_id=999 (FK 없음)
--   팬 팔로우  : fan(id=1) → NOVA(id=1) 초기 팔로우 상태
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 팬 계정 (id=1 고정 → JWT sub=1 → X-Fan-Id: 1)
--    비밀번호: Test1234!  (BCrypt strength=10)
-- ============================================================
INSERT INTO fan (id, email, nickname, auth_provider, provider_id, password_hash, is_allow_notification, created_at)
VALUES (1, 'fan@fandrops.test', '테스트팬', 'LOCAL', 'local-fe-1',
        '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re',
        true, NOW())
ON DUPLICATE KEY UPDATE
    email            = VALUES(email),
    nickname         = VALUES(nickname),
    password_hash    = VALUES(password_hash);

-- ============================================================
-- 2. 아티스트 프로필 (id 고정: FE hardcode 1·2·3)
--    agency_id=999 — FK 제약 없으므로 임의값 사용 (V11 설계 의도)
-- ============================================================
INSERT INTO artist_profile (id, agency_id, name, fan_count, joined_at, bio)
VALUES
    (1, 999, 'NOVA', 0, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 NOVA'),
    (2, 999, 'LUNA', 0, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 LUNA'),
    (3, 999, 'ECHO', 0, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 ECHO')
ON DUPLICATE KEY UPDATE
    name   = VALUES(name),
    bio    = VALUES(bio);

-- ============================================================
-- 2-1. 입점 승인 에이전시 + 아티스트 프로필 (Admin 심사 APPROVED 데모)
-- ============================================================
INSERT INTO agency_account (id, login_id, password_hash, company_name, contact_email, status, role, created_at)
VALUES (2, 'globalstar@example.com',
        '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re',
        '글로벌 스타 엔터', 'globalstar@example.com', 'ACTIVE', 'AGENCY',
        DATE_SUB(NOW(), INTERVAL 8 DAY))
ON DUPLICATE KEY UPDATE company_name=VALUES(company_name);

INSERT INTO artist_profile (id, agency_id, name, fan_count, joined_at, bio)
VALUES (4, 2, 'PRISM', 0, DATE_SUB(NOW(), INTERVAL 8 DAY),
        '입점 승인 데모 — Admin 심사 APPROVED 후 생성된 아티스트 그룹')
ON DUPLICATE KEY UPDATE name=VALUES(name), agency_id=VALUES(agency_id);

-- ============================================================
-- 3. 아티스트 피드
--    artist_member_id=1 = 팬(id=1)이 아티스트 멤버 역할로 작성
--    (로컬 프로파일: anyRequest().permitAll() — 권한 검증 없음)
-- ============================================================

-- 기존 테스트 피드 초기화 (재실행 시 중복 방지)
DELETE FROM artist_feed WHERE artist_id IN (1, 2, 3) AND artist_member_id = 1;

-- NOVA 피드 5개
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (1, 1, 'NOVA 팬 여러분, 안녕하세요! 🎵 드디어 컴백 준비가 시작됐어요.',    12, 3, NOW(6) - INTERVAL 5  DAY),
    (1, 1, '오늘 뮤직비디오 촬영 완료! 기대해주세요 📸',                        27, 8, NOW(6) - INTERVAL 3  DAY),
    (1, 1, '새 앨범 타이틀곡 작업 중 🎶 힌트: 여름 느낌 물씬~',                  9, 1, NOW(6) - INTERVAL 1  DAY),
    (1, 1, '오늘 라이브 방송 22:00 KST 시작합니다! 기다려줘서 고마워요 💖',      45, 15, NOW(6) - INTERVAL 12 HOUR),
    (1, 1, '드롭스 굿즈 최종 디자인 확정됐어요. 곧 공개 예정 👀',                 6, 0, NOW(6) - INTERVAL 1  HOUR);

-- LUNA 피드 3개
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (2, 1, 'LUNA 1주년을 함께해줘서 정말 감사해요 💜 팬 여러분 최고!',           33, 11, NOW(6) - INTERVAL 4  DAY),
    (2, 1, '포토카드 세트 패키지 디자인 비하인드 공개 📦',                        18, 5, NOW(6) - INTERVAL 2  DAY),
    (2, 1, '다음 주 팬미팅 일정 공지 드립니다. 꼭 확인해주세요!',                  7, 2, NOW(6) - INTERVAL 6  HOUR);

-- ECHO 피드 3개
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (3, 1, 'ECHO 솔로 데뷔 앨범 발매 D-7 🎤 Limited Vinyl 예약 오픈!',          51, 20, NOW(6) - INTERVAL 7  DAY),
    (3, 1, '레코딩 스튜디오에서 열심히 작업 중 🌙 곧 만나요.',                    14, 4, NOW(6) - INTERVAL 2  DAY),
    (3, 1, '오늘 인스타 라이브 예정! 솔로 앨범 수록곡 일부 공개됩니다 🎵',          8, 1, NOW(6) - INTERVAL 3  HOUR);

-- ============================================================
-- 4. 팬 팔로우 (fan_id=1 → NOVA artist_id=1)
--    FE 초기 상태: NOVA는 이미 팔로우, LUNA·ECHO는 미팔로우
-- ============================================================
INSERT INTO user_follow (fan_id, artist_id, followed_at)
VALUES (1, 1, NOW(6))
ON DUPLICATE KEY UPDATE followed_at = VALUES(followed_at);

-- ============================================================
-- 5. artist_profile fan_count 동기화
-- ============================================================
UPDATE artist_profile ap
SET fan_count = (SELECT COUNT(*) FROM user_follow uf WHERE uf.artist_id = ap.id)
WHERE ap.id IN (1, 2, 3);

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 확인용 조회
-- ============================================================
SELECT 'fan'           AS 테이블, COUNT(*) AS 건수 FROM fan          WHERE id = 1
UNION ALL
SELECT 'artist_profile',                COUNT(*) FROM artist_profile WHERE id IN (1,2,3)
UNION ALL
SELECT 'artist_feed(NOVA)',             COUNT(*) FROM artist_feed    WHERE artist_id = 1
UNION ALL
SELECT 'artist_feed(LUNA)',             COUNT(*) FROM artist_feed    WHERE artist_id = 2
UNION ALL
SELECT 'artist_feed(ECHO)',             COUNT(*) FROM artist_feed    WHERE artist_id = 3
UNION ALL
SELECT 'user_follow(fan1→NOVA)',        COUNT(*) FROM user_follow    WHERE fan_id = 1 AND artist_id = 1;