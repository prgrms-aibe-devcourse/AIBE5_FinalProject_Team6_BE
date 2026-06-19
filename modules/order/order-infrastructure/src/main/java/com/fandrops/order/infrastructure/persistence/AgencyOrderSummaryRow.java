package com.fandrops.order.infrastructure.persistence;

import java.time.LocalDateTime;

/** Native SQL 결과를 매핑하는 Spring Data 프로젝션 인터페이스. Agency Order Management 화면용. */
public interface AgencyOrderSummaryRow {
    Long getOrderId();
    String getStatus();
    Integer getTotalAmount();
    LocalDateTime getCreatedAt();
    Long getArtistId();
    String getArtistName();
    String getFirstProductName();
    Long getItemCount();
}
