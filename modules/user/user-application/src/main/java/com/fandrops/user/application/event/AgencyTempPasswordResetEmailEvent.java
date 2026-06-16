package com.fandrops.user.application.event;

/** DB 커밋 후 임시 비밀번호 재발급 이메일 발송을 트리거하는 이벤트. tempPassword는 평문 — 이메일 전송에만 사용. */
public record AgencyTempPasswordResetEmailEvent(
        String email,
        String loginId,
        String tempPassword
) {
    @Override
    public String toString() {
        return "AgencyTempPasswordResetEmailEvent[email=" + email + ", loginId=" + loginId + ", tempPassword=***]";
    }
}
