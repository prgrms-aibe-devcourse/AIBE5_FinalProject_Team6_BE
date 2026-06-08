package com.fandrops.order.domain;

import lombok.Getter;

/** 상품 상세 조회 시 재고 현황을 전달하기 위한 값 객체. */
@Getter
public class InventoryInfo {
    private final int totalQty;
    private final int reservedQty;
    private final int availableQty;

    public InventoryInfo(int totalQty, int reservedQty, int availableQty) {
        this.totalQty = totalQty;
        this.reservedQty = reservedQty;
        this.availableQty = availableQty;
    }
}
