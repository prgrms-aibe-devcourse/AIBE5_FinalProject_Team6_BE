package com.fandrops.community.api.schedule;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.api.CommunityControllerSupport;
import com.fandrops.community.application.schedule.EventCreateCommand;
import com.fandrops.community.application.schedule.LiveStartResult;
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

        Long artistMemberId = resolveArtistMemberId(authentication, artistMemberIdHeader);
        ScheduleResult result = scheduleService.createEvent(
                new EventCreateCommand(artistId, artistMemberId, request.title(),
                        request.type(), request.scheduledAt()));
        return ResponseEntity.status(201).body(ApiResponse.ok(Map.of("eventId", result.id()), traceId()));
    }

    // GET /api/v1/artists/{artistId}/calendar?from=...&to=... — 통합 스케줄 조회 (F03-05)
    @GetMapping("/api/v1/artists/{artistId}/calendar")
    public ResponseEntity<ApiResponse<Map<String, List<ScheduleResult>>>> getCalendar(
            @PathVariable Long artistId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        List<ScheduleResult> events = scheduleService.getCalendar(artistId, from, to);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("events", events), traceId()));
    }

    // PATCH /api/v1/lives/{liveId}/start — 라이브 시작 + ARTIST_SCHEDULE 알림 이벤트 발행 (F03-06)
    @PatchMapping("/api/v1/lives/{liveId}/start")
    public ResponseEntity<ApiResponse<LiveStartResult>> startLive(
            @PathVariable Long liveId,
            Authentication authentication,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        Long artistMemberId = resolveArtistMemberId(authentication, artistMemberIdHeader);
        LiveStartResult result = scheduleService.startLive(liveId, artistMemberId);
        return ResponseEntity.ok(ApiResponse.ok(result, traceId()));
    }

}