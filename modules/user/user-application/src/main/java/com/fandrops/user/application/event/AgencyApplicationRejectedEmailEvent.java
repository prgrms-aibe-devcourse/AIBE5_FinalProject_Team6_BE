package com.fandrops.user.application.event;

/** DB 커밋 후 입점 반려 이메일 발송을 트리거하는 이벤트. */
public record AgencyApplicationRejectedEmailEvent(
        String email,
        String rejectReason
) {
    @Override
    public String toString() {
        return "AgencyApplicationRejectedEmailEvent[email=" + email + ", rejectReason=***]";
    }
}
