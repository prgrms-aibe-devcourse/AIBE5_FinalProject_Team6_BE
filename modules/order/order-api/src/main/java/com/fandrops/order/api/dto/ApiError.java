package com.fandrops.order.api.dto;

import lombok.Getter;

/** 실패 응답의 에러 정보. code·message·retryable을 담는다. */
@Getter
public class ApiError {

    private final String code;
    private final String message;
    private final boolean retryable;

    public ApiError(String code, String message, boolean retryable) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
    }
}
