package com.fandrops.payment.api.queue;

// 현재 스펙: docs/state/invariants-and-state-machines.md §6 기준 1:1 매핑
public enum ApiQueueStatus {
    WAITING, PROCESSING, DONE, EXPIRED;

    public static ApiQueueStatus from(String domainStatus) {
        return ApiQueueStatus.valueOf(domainStatus);
    }
}
