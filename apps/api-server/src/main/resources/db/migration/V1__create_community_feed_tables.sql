-- ============================================================
-- F03-02 Community Feed 테이블 (ERD §5)
-- ============================================================

CREATE TABLE artist_feed
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    artist_id        BIGINT       NOT NULL,
    artist_member_id BIGINT       NOT NULL,
    content          TEXT         NOT NULL,
    like_count       INT          NOT NULL DEFAULT 0,
    comment_count    INT          NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

-- 아티스트 피드 목록 조회: artist_id 필터 + id DESC 정렬
CREATE INDEX idx_artist_feed_artist_cursor ON artist_feed (artist_id, id DESC);

-- ============================================================

CREATE TABLE feed_image
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    feed_id    BIGINT        NOT NULL,
    image_url  VARCHAR(2048) NOT NULL,
    created_at DATETIME(6)   NOT NULL,  -- 등록 순서 = 표시 순서 (ERD §5.1)
    PRIMARY KEY (id)
);

CREATE INDEX idx_feed_image_feed_created ON feed_image (feed_id, created_at);

-- ============================================================
-- ERD §5.4: fanId XOR artistMemberId 유니크 — NULL은 중복 허용(MySQL 정책)이므로
--            실제 중복 방지는 (non-null 컬럼, feed_id) 조합에서 동작
CREATE TABLE feed_like
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    feed_id           BIGINT      NOT NULL,
    fan_id            BIGINT      NULL,
    artist_member_id  BIGINT      NULL,
    artist_id         BIGINT      NULL,
    created_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_feed_like_fan (fan_id, feed_id),
    UNIQUE KEY uq_feed_like_artist (artist_member_id, feed_id)
);

-- ============================================================
-- ERD §5.3: fanId XOR artistMemberId 필수 — CHECK 제약 권장
CREATE TABLE comment
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    feed_id           BIGINT      NOT NULL,
    artist_id         BIGINT      NOT NULL,   -- 비정규화: 마이페이지 JOIN 제거 (ERD §5.3)
    fan_id            BIGINT      NULL,
    artist_member_id  BIGINT      NULL,
    parent_id         BIGINT      NULL,        -- self FK: 대댓글
    content           TEXT        NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 아티스트는 답글만 가능 (애플리케이션 레벨 강제, DB 참고용)
    CONSTRAINT chk_comment_author CHECK (
        (fan_id IS NOT NULL) != (artist_member_id IS NOT NULL)
    ),
    CONSTRAINT chk_artist_reply_only CHECK (
        artist_member_id IS NULL OR parent_id IS NOT NULL
    )
);

CREATE INDEX idx_comment_feed_cursor ON comment (feed_id, id);
CREATE INDEX idx_comment_fan_cursor  ON comment (fan_id, id DESC);

-- ============================================================

CREATE TABLE comment_like
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    comment_id BIGINT      NOT NULL,
    fan_id     BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_comment_like_fan (fan_id, comment_id)
);

CREATE INDEX idx_comment_like_comment ON comment_like (comment_id);