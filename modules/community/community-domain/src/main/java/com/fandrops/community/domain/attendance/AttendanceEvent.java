package com.fandrops.community.domain.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceEvent {

    private final Long id;
    private final Long artistId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String rewardDesc;
    private final boolean active;
    private final LocalDateTime createdAt;

    private AttendanceEvent(Long id, Long artistId, LocalDate startDate, LocalDate endDate,
                            String rewardDesc, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.artistId = artistId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.rewardDesc = rewardDesc;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static AttendanceEvent reconstruct(Long id, Long artistId, LocalDate startDate,
                                              LocalDate endDate, String rewardDesc,
                                              boolean active, LocalDateTime createdAt) {
        return new AttendanceEvent(id, artistId, startDate, endDate, rewardDesc, active, createdAt);
    }

    public boolean isOngoing(LocalDate today) {
        return active && !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    public Long getId() { return id; }
    public Long getArtistId() { return artistId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getRewardDesc() { return rewardDesc; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}