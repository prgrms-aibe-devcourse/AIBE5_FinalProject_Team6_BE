package com.fandrops.community.domain.attendance;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceLog {

    private Long id;
    private final Long eventId;
    private final Long fanId;
    private final LocalDate checkedDate;
    private final LocalDateTime createdAt;

    private AttendanceLog(Long id, Long eventId, Long fanId,
                          LocalDate checkedDate, LocalDateTime createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.fanId = fanId;
        this.checkedDate = checkedDate;
        this.createdAt = createdAt;
    }

    public static AttendanceLog create(Long eventId, Long fanId, Clock clock) {
        return new AttendanceLog(null, eventId, fanId,
                LocalDate.now(clock), LocalDateTime.now(clock));
    }

    public static AttendanceLog reconstruct(Long id, Long eventId, Long fanId,
                                            LocalDate checkedDate, LocalDateTime createdAt) {
        return new AttendanceLog(id, eventId, fanId, checkedDate, createdAt);
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getFanId() { return fanId; }
    public LocalDate getCheckedDate() { return checkedDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}