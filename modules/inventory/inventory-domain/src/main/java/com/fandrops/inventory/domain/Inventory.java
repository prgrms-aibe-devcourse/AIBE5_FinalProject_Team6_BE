package com.fandrops.inventory.domain;

import com.fandrops.inventory.domain.exception.InvalidInventoryStateException;
import com.fandrops.inventory.domain.exception.OutOfStockException;
import com.fandrops.inventory.domain.exception.ReserveFailedException;
import lombok.Getter;

@Getter
public class Inventory {

    private final Long id;
    private final Long productId;
    private int totalQty;
    private int reservedQty;
    private int availableQty;
    private final int version;

    private Inventory(Long id, Long productId, int totalQty, int reservedQty, int availableQty, int version) {
        this.id = id;
        this.productId = productId;
        this.totalQty = totalQty;
        this.reservedQty = reservedQty;
        this.availableQty = availableQty;
        this.version = version;
    }

    public static Inventory create(Long productId, int initialQty) {
        return new Inventory(null, productId, initialQty, 0, initialQty, 0);
    }

    public static Inventory reconstitute(Long id, Long productId, int totalQty, int reservedQty, int availableQty, int version) {
        return new Inventory(id, productId, totalQty, reservedQty, availableQty, version);
    }

    // I-1, I-2: OUT_OF_STOCK(ERR_4004) vs RESERVE_FAILED(ERR_4005) 분리
    public InventoryHistory reserve(int qty, Long orderId) {
        if (availableQty == 0) {
            throw new OutOfStockException(productId);
        }
        if (availableQty < qty) {
            throw new ReserveFailedException(productId, qty, availableQty);
        }
        int before = availableQty;
        reservedQty += qty;
        availableQty -= qty;
        return InventoryHistory.of(id, InventoryChangeType.RESERVE, qty, before, availableQty, orderId, InventoryRefType.ORDER);
    }

    // I-1, I-3: reservedQty 음수 방지 가드 + COMPLETED 주문만 totalQty 영구 차감
    public InventoryHistory confirm(int qty, Long orderId) {
        if (reservedQty < qty) {
            throw new InvalidInventoryStateException(
                String.format("confirm 실패: reservedQty=%d, qty=%d", reservedQty, qty));
        }
        int before = totalQty;
        totalQty -= qty;
        reservedQty -= qty;
        return InventoryHistory.of(id, InventoryChangeType.DECREASE, qty, before, totalQty, orderId, InventoryRefType.ORDER);
    }

    // I-1: reservedQty 음수 방지 가드 + Saga 보상, 사용자 취소 시 선점 해제
    public InventoryHistory restore(int qty, Long orderId) {
        if (reservedQty < qty) {
            throw new InvalidInventoryStateException(
                String.format("restore 실패: reservedQty=%d, qty=%d", reservedQty, qty));
        }
        int before = availableQty;
        reservedQty -= qty;
        availableQty += qty;
        return InventoryHistory.of(id, InventoryChangeType.RELEASE, qty, before, availableQty, orderId, InventoryRefType.ORDER);
    }

    // 재입고
    public InventoryHistory increase(int qty, Long restockId) {
        int before = totalQty;
        totalQty += qty;
        availableQty += qty;
        return InventoryHistory.of(id, InventoryChangeType.INCREASE, qty, before, totalQty, restockId, InventoryRefType.RESTOCK);
    }

    // I-4: available_qty = 0 이면 SOLD_OUT 판단 근거
    public boolean isSoldOut() {
        return availableQty == 0;
    }

}
