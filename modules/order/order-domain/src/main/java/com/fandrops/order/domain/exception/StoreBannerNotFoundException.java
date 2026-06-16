package com.fandrops.order.domain.exception;

public class StoreBannerNotFoundException extends RuntimeException {
    public StoreBannerNotFoundException(Long bannerId) {
        super("배너를 찾을 수 없습니다: bannerId=" + bannerId);
    }
}
