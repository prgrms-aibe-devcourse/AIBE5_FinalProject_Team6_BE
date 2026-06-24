-- NOVA·LUNA·ECHO·PRISM fan_count 초기값 설정 (팔로워 수 기준 내림차순 정렬용)
UPDATE artist_profile SET fan_count = 20000 WHERE id = 1 AND name = 'NOVA';
UPDATE artist_profile SET fan_count = 15000 WHERE id = 2 AND name = 'LUNA';
UPDATE artist_profile SET fan_count = 12000 WHERE id = 3 AND name = 'ECHO';
UPDATE artist_profile SET fan_count = 10000 WHERE id = 4 AND name = 'PRISM';