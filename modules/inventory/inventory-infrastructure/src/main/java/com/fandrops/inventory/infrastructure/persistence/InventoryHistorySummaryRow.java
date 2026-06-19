package com.fandrops.inventory.infrastructure.persistence;

import java.time.LocalDateTime;

/** Native SQL 결과를 매핑하는 Spring Data 프로젝션 인터페이스. Agency Inventory History 화면용. */
public interface InventoryHistorySummaryRow {
    Long getId();
    Long getInventoryId();
    String getChangeType();
    Integer getQtyDelta();
    Integer getQtyBefore();
    Integer getQtyAfter();
    Long getReferenceId();
    String getRefType();
    LocalDateTime getCreatedAt();
    Long getProductId();
    String getProductName();
}
