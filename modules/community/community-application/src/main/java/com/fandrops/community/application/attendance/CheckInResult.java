package com.fandrops.community.application.attendance;

import java.time.LocalDate;

public record CheckInResult(
        Long eventId,
        LocalDate checkedDate,
        int streakDays
) {}