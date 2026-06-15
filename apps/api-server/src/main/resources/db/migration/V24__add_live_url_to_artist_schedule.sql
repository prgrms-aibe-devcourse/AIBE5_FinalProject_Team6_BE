ALTER TABLE artist_schedule
    ADD COLUMN live_url VARCHAR(2048) NULL COMMENT '유튜브 임베드 URL (LIVE 타입 전용)';

CREATE INDEX idx_artist_schedule_artist_type
    ON artist_schedule (artist_id, type, scheduled_at);