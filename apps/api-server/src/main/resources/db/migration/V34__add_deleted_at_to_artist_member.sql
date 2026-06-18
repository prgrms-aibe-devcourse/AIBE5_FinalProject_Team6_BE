ALTER TABLE artist_member
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD INDEX idx_artist_member_deleted_at (deleted_at);