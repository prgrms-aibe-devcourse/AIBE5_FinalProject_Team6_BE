-- ============================================================
-- [로컬 H2] NOVA/LUNA/ECHO 테스트용 bio 문구 제거
-- 대상: spring.profiles.active=local (H2 in-memory 또는 로컬 DB)
-- 실행: H2 Console 또는 앱 기동 중 JDBC로 1회 실행
-- ============================================================

-- 실행 전 확인
SELECT id, name, bio FROM artist_profile WHERE id IN (1, 2, 3) ORDER BY id;

-- FE 검증용 테스트 문구 제거 (빈 문자열로 초기화)
UPDATE artist_profile SET bio = '' WHERE id = 1 AND name = 'NOVA';
UPDATE artist_profile SET bio = '' WHERE id = 2 AND name = 'LUNA';
UPDATE artist_profile SET bio = '' WHERE id = 3 AND name = 'ECHO';

-- 실행 후 확인
SELECT id, name, bio FROM artist_profile WHERE id IN (1, 2, 3) ORDER BY id;

-- ============================================================
-- [참고] 로컬 아티스트 테스트 계정 (시드 기준, 비밀번호: Test1234!)
--   NovaHaneul  → NOVA 멤버 (하늘)
--   NovaSera    → NOVA 멤버 (세라)
--   LunaEunbyeol → LUNA 멤버 (은별)
--   Echo        → ECHO 솔로
-- FE 로그인 후 role=ARTIST 이면 /artist 로 자동 이동됨
-- ============================================================
