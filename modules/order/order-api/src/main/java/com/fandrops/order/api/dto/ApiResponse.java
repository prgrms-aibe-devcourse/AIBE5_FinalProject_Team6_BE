package com.fandrops.order.api.dto;

import lombok.Getter;

/** 공통 응답 봉투. ok()는 성공, error()는 실패 응답을 생성한다. */
@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ApiError error;
    private final String traceId;

    private ApiResponse(boolean success, T data, ApiError error, String traceId) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, null, traceId);
    }

    public static <T> ApiResponse<T> error(ApiError error, String traceId) {
        return new ApiResponse<>(false, null, error, traceId);
    }
}
