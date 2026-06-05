-- F03-08: 굿즈 투표 (ERD §13)
CREATE TABLE IF NOT EXISTS goods_vote (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    artist_id  BIGINT       NOT NULL,
    title      VARCHAR(255) NOT NULL,
    ends_at    DATETIME     NOT NULL,
    is_active  TINYINT(1)   NOT NULL DEFAULT 1,
    created_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_goods_vote_artist_cursor (artist_id, id DESC)
);

-- vote_count: 동시성 안전 집계 필드 (UPDATE ... SET vote_count = vote_count + 1)
CREATE TABLE IF NOT EXISTS goods_vote_option (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    vote_id    BIGINT        NOT NULL,
    label      VARCHAR(255)  NOT NULL,
    image_url  VARCHAR(2048) NULL,
    vote_count INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_goods_vote_option_vote (vote_id)
);

-- UK(vote_id, fan_id): 1인 1투표 DB 수준 보장
CREATE TABLE IF NOT EXISTS goods_vote_record (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    vote_id   BIGINT      NOT NULL,
    option_id BIGINT      NOT NULL,
    fan_id    BIGINT      NOT NULL,
    voted_at  DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_vote_fan (vote_id, fan_id)
);
