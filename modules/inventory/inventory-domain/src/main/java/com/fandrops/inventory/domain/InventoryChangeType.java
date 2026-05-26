package com.fandrops.inventory.domain;

public enum InventoryChangeType {
    RESERVE,   // 주문 생성 시 재고 선점
    RELEASE,   // 결재 실패, 사용자 취소로 선점 해제
    DECREASE,  // 결제 확정 후 실제 재고 차감
    INCREASE,  // 재입고
    COMPENSATE // 수동 보상
}

