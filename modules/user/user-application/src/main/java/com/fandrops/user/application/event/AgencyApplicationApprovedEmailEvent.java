package com.fandrops.user.application.event;

/** DB 커밋 후 입점 승인 이메일 발송을 트리거하는 이벤트. tempPassword는 평문 — 이메일 전송에만 사용. */
public record AgencyApplicationApprovedEmailEvent(
        String email,
        String loginId,
        String tempPassword
) {}
