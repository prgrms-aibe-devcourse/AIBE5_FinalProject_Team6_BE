package com.fandrops.inventory.application.exception;

/** 동일 (inventory_id, reference_id, ref_type, change_type) 조합으로 이력이 이미 존재할 때. 멱등 중복 호출 차단. */
public class DuplicateHistoryException extends RuntimeException {

    public DuplicateHistoryException(Long inventoryId, Long referenceId) {
        super(String.format("재고 이력이 이미 존재합니다: inventoryId=%d, referenceId=%d", inventoryId, referenceId));
    }
}
