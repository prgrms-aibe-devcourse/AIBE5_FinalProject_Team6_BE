-- V46: 리아 × Drops [FAIRY SIGNAL] 상품·배너 날짜 갱신
-- V45 적용 시점 기준 날짜가 만료되어, 오픈 예정 상품으로 재노출하기 위해 갱신

UPDATE product
SET drops_start_at = DATE_ADD(NOW(), INTERVAL 3 DAY),
    drops_end_at   = DATE_ADD(NOW(), INTERVAL 7 DAY)
WHERE name = '리아 × Drops: [FAIRY SIGNAL] 첫 EP 기념 패키지';

UPDATE banner
SET start_at = DATE_ADD(NOW(), INTERVAL -1 HOUR),
    end_at   = DATE_ADD(NOW(), INTERVAL 7 DAY)
WHERE title = '리아 × Drops — FAIRY SIGNAL 오픈 예정'
  AND banner_type = 'STORE';