package com.fandrops.payment.api.queue;

public class SseCapacityExceededException extends RuntimeException {

    public SseCapacityExceededException() {
        super("SSE 연결이 가득 찼습니다. 잠시 후 다시 시도해주세요.");
    }
}