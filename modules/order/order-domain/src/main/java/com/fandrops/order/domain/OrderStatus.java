package com.fandrops.order.domain;

public enum OrderStatus {
    /** 주문 생성 직후, 재고 예약 전 */
    PENDING,

    /** 재고 선점 완료, 결제 대기 중 */
    RESERVED,

    /** PG 결제 승인 확정 시점 */
    PAID,

    /** 결제 실패 직후 경유 상태 — Saga 보상 완료 후 반드시 CANCELLED로 전이, 최종 상태 아님 */
    FAILED,

    /** 재고 차감 및 알림 발행까지 모든 후처리 완료 (최종) */
    COMPLETED,

    /** 재고 부족 / 결제 실패 / 사용자 취소로 주문 종료 (최종) */
    CANCELLED
}
