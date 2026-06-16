-- artist_schedule: (artist_id, type, id DESC) 커서 페이징용 복합 인덱스
-- type 필터 + id DESC 정렬 조합 지원 (V4에는 (artist_id, scheduled_at)만 존재)
CREATE INDEX idx_artist_schedule_artist_type_id
    ON artist_schedule (artist_id, type, id DESC);
