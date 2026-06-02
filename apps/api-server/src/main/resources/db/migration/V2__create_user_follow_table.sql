-- ============================================================
-- F01-04 USER_FOLLOW: 팬 가입(팔로우) 테이블
-- FK 의도적 생략: MSA 전환 대비 + 앱 레벨 cascade로 정합성 보장
-- ============================================================

CREATE TABLE user_follow
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    fan_id      BIGINT      NOT NULL,
    artist_id   BIGINT      NOT NULL,
    followed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_follow (fan_id, artist_id)
);

-- /fans/me/artists 팬 가입 아티스트 목록 커서 페이징
CREATE INDEX idx_user_follow_fan    ON user_follow (fan_id, id DESC);
-- 아티스트 팬 수 조회 + 팬 가입 여부 체크
CREATE INDEX idx_user_follow_artist ON user_follow (artist_id, fan_id);