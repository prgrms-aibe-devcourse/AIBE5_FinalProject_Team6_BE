-- V47: NOVA·LUNA·ECHO·PRISM 기본 계정 및 아티스트 멤버 추가
-- V99(로컬 전용)에만 존재하던 계정을 프로덕션 DB에 반영한다.

-- 1. 기본 에이전시 (테스트 기획사, 글로벌 스타 엔터)
INSERT IGNORE INTO agency_account (id, login_id, password_hash, company_name, contact_email, status, role, created_at)
VALUES
    (1, 'agency@fandrops.test',    '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '테스트 기획사',      'agency@fandrops.test',      'ACTIVE', 'AGENCY', NOW()),
    (2, 'globalstar@example.com',  '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '글로벌 스타 엔터',  'globalstar@example.com',    'ACTIVE', 'AGENCY', NOW());

-- 2. 기본 아티스트 프로필 (NOVA·LUNA·ECHO·PRISM)
INSERT IGNORE INTO artist_profile (id, agency_id, name, fan_count, joined_at, bio)
VALUES
    (1, 1, 'NOVA',  20000, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 NOVA'),
    (2, 1, 'LUNA',  15000, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 LUNA'),
    (3, 1, 'ECHO',  12000, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 ECHO'),
    (4, 2, 'PRISM', 10000, '2024-01-01 00:00:00', '입점 승인 데모 아티스트 그룹 PRISM');

-- 3. 아티스트 멤버 로그인 계정 (비밀번호: Test1234!)
INSERT IGNORE INTO artist_member (id, artist_id, login_id, password_hash, member_name, role)
VALUES
    (1, 1, 'NovaHaneul',   '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '하늘', 'ARTIST'),
    (2, 1, 'NovaSera',     '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '세라', 'ARTIST'),
    (3, 1, 'NovaMina',     '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '미나', 'ARTIST'),
    (4, 1, 'NovaYujin',    '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '유진', 'ARTIST'),
    (5, 2, 'LunaEunbyeol', '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '은별', 'ARTIST'),
    (6, 2, 'LunaDal',      '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '달',   'ARTIST'),
    (7, 2, 'LunaHaneul',   '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '하늘', 'ARTIST'),
    (8, 2, 'LunaSeoyeon',  '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '서연', 'ARTIST'),
    (9, 3, 'Echo',         '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', 'ECHO', 'ARTIST');