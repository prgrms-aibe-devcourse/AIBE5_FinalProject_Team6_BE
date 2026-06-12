package com.fandrops.community.api.attendance;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.api.CommunityControllerSupport;
import com.fandrops.community.application.attendance.AttendanceEventResult;
import com.fandrops.community.application.attendance.AttendanceService;
import com.fandrops.community.application.attendance.CheckInResult;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AttendanceController extends CommunityControllerSupport {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService, Environment environment) {
        super(environment);
        this.attendanceService = attendanceService;
    }

    // GET /api/v1/artists/{artistId}/attendance-events — 진행 중인 출석 이벤트 목록
    @GetMapping("/api/v1/artists/{artistId}/attendance-events")
    public ResponseEntity<ApiResponse<List<AttendanceEventResult>>> getActiveEvents(
            @PathVariable Long artistId) {

        List<AttendanceEventResult> results = attendanceService.getActiveEvents(artistId);
        return ResponseEntity.ok(ApiResponse.ok(results, traceId()));
    }

    // POST /api/v1/attendance-events/{eventId}/check-in — 출석 체크
    @PostMapping("/api/v1/attendance-events/{eventId}/check-in")
    public ResponseEntity<ApiResponse<CheckInResult>> checkIn(
            @PathVariable Long eventId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        CheckInResult result = attendanceService.checkIn(eventId, fanId);
        return ResponseEntity.status(201).body(ApiResponse.ok(result, traceId()));
    }
}