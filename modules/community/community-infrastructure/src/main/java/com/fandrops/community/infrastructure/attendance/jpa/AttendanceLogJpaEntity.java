package com.fandrops.community.infrastructure.attendance.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_attendance_log",
                columnNames = {"event_id", "fan_id", "checked_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AttendanceLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "fan_id", nullable = false)
    private Long fanId;

    @Column(name = "checked_date", nullable = false)
    private LocalDate checkedDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}