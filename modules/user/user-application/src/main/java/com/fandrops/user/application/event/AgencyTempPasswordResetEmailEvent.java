package com.fandrops.user.application.event;

public record AgencyTempPasswordResetEmailEvent(String email, String loginId, String tempPassword) {}
