package com.fandrops.community.api.schedule;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.api.CommunityControllerSupport;
import com.fandrops.community.application.exception.ForbiddenException;
import com.fandrops.community.application.schedule.EventCreateCommand;
import com.fandrops.community.application.schedule.LiveCreateCommand;
import com.fandrops.community.application.schedule.ScheduleResult;
import com.fandrops.community.application.schedule.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
public class ScheduleController extends CommunityControllerSupport {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService, Environment environment) {
        super(environment);
        this.scheduleService = scheduleService;
    }

    // POST /api/v1/artists/{artistId}/events — 행사·일정 등록 (F05-01, AGENCY·아티스트 멤버)
    @PostMapping("/api/v1/artists/{artistId}/events")
    public ResponseEntity<ApiResponse<Map<String, Long>>> createEvent(
            @PathVariable Long artistId,
            @Valid @RequestBody EventCreateRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        if (!isLocalProfile() && (authentication == null || !hasArtistOrAgencyRole(authentication))) {
            throw new ForbiddenException("ARTIST 또는 AGENCY 권한이 필요합니다.");
        }
        Long artistMemberId = resolveArtistMemberId(authentication, artistMemberIdHeader);
        ScheduleResult result = scheduleService.createEvent(
                new EventCreateCommand(artistId, artistMemberId, request.title(),
                        request.type(), request.scheduledAt()));
        return ResponseEntity.status(201).body(ApiResponse.ok(Map.of("eventId", result.id()), traceId()));
    }

    // PATCH /api/v1/lives/{scheduleId}/start — 라이브 시작·ARTIST_SCHEDULE 알림 발행 (F03-04, ARTIST·AGENCY)
    @PatchMapping("/api/v1/lives/{scheduleId}/start")
    public ResponseEntity<ApiResponse<ScheduleResult>> startLive(
            @PathVariable Long scheduleId,
            Authentication authentication,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        if (!isLocalProfile() && (authentication == null || !hasArtistOrAgencyRole(authentication))) {
            throw new ForbiddenException("ARTIST 또는 AGENCY 권한이 필요합니다.");
        }
        Long artistMemberId = resolveArtistMemberId(authentication, artistMemberIdHeader);
        ScheduleResult result = scheduleService.startLive(scheduleId, artistMemberId);
        return ResponseEntity.ok(ApiResponse.ok(result, traceId()));
    }

    // POST /api/v1/artists/{artistId}/lives — 라이브 등록 (F03-07, AGENCY)
    @PostMapping("/api/v1/artists/{artistId}/lives")
    public ResponseEntity<ApiResponse<Map<String, Long>>> registerLive(
            @PathVariable Long artistId,
            @Valid @RequestBody LiveCreateRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        if (!isLocalProfile() && (authentication == null || !hasAgencyRole(authentication))) {
            throw new ForbiddenException("AGENCY 권한이 필요합니다.");
        }
        Long artistMemberId = resolveArtistMemberId(authentication, artistMemberIdHeader);
        ScheduleResult result = scheduleService.registerLive(
                new LiveCreateCommand(artistId, artistMemberId,
                        request.title(), request.scheduledAt(), request.liveUrl()));
        return ResponseEntity.status(201).body(ApiResponse.ok(Map.of("scheduleId", result.id()), traceId()));
    }

    // GET /api/v1/artists/{artistId}/lives — 활성 라이브 목록 (F03-07, 공개)
    @GetMapping("/api/v1/artists/{artistId}/lives")
    public ResponseEntity<ApiResponse<Map<String, List<ScheduleResult>>>> getLives(
            @PathVariable Long artistId) {

        List<ScheduleResult> lives = scheduleService.getLives(artistId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("lives", lives), traceId()));
    }

    // GET /api/v1/artists/{artistId}/calendar?from=...&to=... — 통합 스케줄 조회 (F03-05)
    // 의도적 익명 허용 — F03-05 캘린더는 공개 조회 (mvp-api-spec.md)
    @GetMapping("/api/v1/artists/{artistId}/calendar")
    public ResponseEntity<ApiResponse<Map<String, List<ScheduleResult>>>> getCalendar(
            @PathVariable Long artistId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        List<ScheduleResult> events = scheduleService.getCalendar(artistId, from, to);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("events", events), traceId()));
    }
}