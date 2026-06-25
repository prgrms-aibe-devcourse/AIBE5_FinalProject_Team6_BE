-- payload 컬럼을 JSON → TEXT로 변경한다.
-- Hibernate 6은 String 필드에 columnDefinition="json"이 있으면 Jackson으로 직렬화해
-- 저장하는데 읽기 경로에서 역직렬화가 누락되어 재시도마다 payload가 이중 이스케이프된다.
-- TEXT로 변경하면 문자열을 그대로 저장/조회한다.
ALTER TABLE notification_outbox_events
    MODIFY COLUMN payload TEXT NOT NULL;