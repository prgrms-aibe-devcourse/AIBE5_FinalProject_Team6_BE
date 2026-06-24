-- 로컬 개발 전용 시드 데이터 (H2 in-memory)
-- 운영/스테이징 환경에는 적용되지 않음 (spring.sql.init.mode=embedded)

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
VALUES
    (1,  100, 0, 100, 0),
    (2,  50,  0, 50,  0),
    (3,  10,  0, 10,  0),
    (4,  1,   0, 1,   0),
    (5,  0,   0, 0,   0),
    (22,    100, 0, 100, 0);
