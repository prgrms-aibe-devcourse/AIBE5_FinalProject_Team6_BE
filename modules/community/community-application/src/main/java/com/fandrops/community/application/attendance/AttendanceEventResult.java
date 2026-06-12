package com.fandrops.community.application.attendance;

import java.time.LocalDate;

public record AttendanceEventResult(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String rewardDesc
) {}