-- ============================================================
-- F06 Payment 테이블 (ERD §3)
-- FK 의도적 생략: MSA 전환 대비 + 앱 레벨 정합성 보장
-- ============================================================

CREATE TABLE payment
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,              -- FK 생략: 앱 레벨 정합성
    payment_key VARCHAR(255) NULL,                  -- tossPaymentKey (웹훅 수신 전 NULL 가능)
    amount      BIGINT       NOT NULL,
    method      VARCHAR(100) NULL,                  -- 결제 수단 (웹훅 수신 후 기록)
    status      VARCHAR(50)  NOT NULL,              -- PENDING / SUCCESS / FAILED
    paid_at     DATETIME(6)  NULL,                  -- 결제 성공 시각
    failed_at   DATETIME(6)  NULL,                  -- 결제 실패 시각
    created_at  DATETIME(6)  NOT NULL,              -- 불변 (updatable = false)
    version     INT          NOT NULL DEFAULT 0,    -- 낙관적 락 버전
    PRIMARY KEY (id),
    UNIQUE KEY uq_payment_key (payment_key)         -- tossPaymentKey 멱등성 보장
);

-- 타임아웃 Job: findByStatusAndCreatedAtBefore (PENDING 상태 + 기준 시각 이전 조회)
CREATE INDEX idx_payment_status_created ON payment (status, created_at);

-- 주문 기준 조회: findByOrderId / findByOrderIdForUpdate
CREATE INDEX idx_payment_order_id ON payment (order_id);