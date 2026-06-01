package com.fandrops.common;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error,
        String traceId
) {
    public record ErrorDetail(String code, String message, boolean retryable) {}

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, null, traceId);
    }

    public static <T> ApiResponse<T> fail(String code, String message, boolean retryable, String traceId) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message, retryable), traceId);
    }
}