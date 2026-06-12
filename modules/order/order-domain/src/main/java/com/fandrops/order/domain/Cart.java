package com.fandrops.order.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Cart {

    private Long id;
    private Long fanId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Cart(Long id, Long fanId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.fanId = fanId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Cart create(Long fanId) {
        return new Cart(null, fanId, LocalDateTime.now(), LocalDateTime.now());
    }

    public static Cart of(Long id, Long fanId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Cart(id, fanId, createdAt, updatedAt);
    }
}

