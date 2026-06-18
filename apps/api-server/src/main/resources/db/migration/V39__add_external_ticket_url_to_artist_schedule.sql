ALTER TABLE artist_schedule
    ADD COLUMN external_ticket_url VARCHAR(2048) NULL COMMENT '외부 티켓 예매 URL (EVENT 타입 전용)';