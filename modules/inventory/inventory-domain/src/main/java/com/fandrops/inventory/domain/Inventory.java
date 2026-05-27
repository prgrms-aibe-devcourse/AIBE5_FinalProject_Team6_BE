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
    // 낙관적 잠금용 버전. 체크·증가는 Repository(JPA @Version)가 담당하므로 도메인에서 변경하지 않는다.
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
        validatePositiveQty(initialQty);
        return new Inventory(null, productId, initialQty, 0, initialQty, 0);
    }

    public static Inventory reconstitute(Long id, Long productId, int totalQty, int reservedQty, int availableQty, int version) {
        if (totalQty < 0 || reservedQty < 0 || availableQty < 0) {
            throw new InvalidInventoryStateException(
                String.format("음수 수량 불가: totalQty=%d, reservedQty=%d, availableQty=%d", totalQty, reservedQty, availableQty));
        }
        if (availableQty != totalQty - reservedQty) {
            throw new InvalidInventoryStateException(
                String.format("불변식 위반: availableQty=%d, totalQty=%d, reservedQty=%d", availableQty, totalQty, reservedQty));
        }
        return new Inventory(id, productId, totalQty, reservedQty, availableQty, version);
    }

    // I-1, I-2: OUT_OF_STOCK(ERR_4004) vs RESERVE_FAILED(ERR_4005) 분리
    // qtyBefore/qtyAfter = availableQty 기준
    public InventoryHistory reserve(int qty, Long orderId) {
        validatePositiveQty(qty);
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
    // qtyBefore/qtyAfter = totalQty 기준
    public InventoryHistory confirm(int qty, Long orderId) {
        validatePositiveQty(qty);
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
    // qtyBefore/qtyAfter = availableQty 기준
    public InventoryHistory restore(int qty, Long orderId) {
        validatePositiveQty(qty);
        if (reservedQty < qty) {
            throw new InvalidInventoryStateException(
                String.format("restore 실패: reservedQty=%d, qty=%d", reservedQty, qty));
        }
        int before = availableQty;
        reservedQty -= qty;
        availableQty += qty;
        return InventoryHistory.of(id, InventoryChangeType.RELEASE, qty, before, availableQty, orderId, InventoryRefType.ORDER);
    }

    // 재입고. qtyBefore/qtyAfter = totalQty 기준
    public InventoryHistory increase(int qty, Long restockId) {
        validatePositiveQty(qty);
        int before = totalQty;
        totalQty += qty;
        availableQty += qty;
        return InventoryHistory.of(id, InventoryChangeType.INCREASE, qty, before, totalQty, restockId, InventoryRefType.RESTOCK);
    }

    // TODO: compensate(int delta, Long referenceId) 미구현
    //   - InventoryChangeType.COMPENSATE(수동 보상)에 대응하는 메서드 필요
    //   - 결정 필요: delta 음수 허용 여부, totalQty·availableQty·reservedQty 중 조정 대상
    //   - InventoryRefType에 MANUAL 등 보상 전용 값 추가 필요
    //   - 관련 이슈: feat/12 PR 리뷰 P2 "COMPENSATE에 대응 메서드 없음"

    // I-4: available_qty = 0 이면 SOLD_OUT 판단 근거
    public boolean isSoldOut() {
        return availableQty == 0;
    }

    private static void validatePositiveQty(int qty) {
        if (qty <= 0) throw new InvalidInventoryStateException("qty는 양수여야 합니다: " + qty);
    }

}
