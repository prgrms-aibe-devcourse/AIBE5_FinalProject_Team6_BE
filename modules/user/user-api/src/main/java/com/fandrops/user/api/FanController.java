package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.UpdateFanRequest;
import com.fandrops.user.application.dto.FanResult;
import com.fandrops.user.application.dto.UpdateFanCommand;
import com.fandrops.user.application.service.AuthService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fans")
public class FanController extends UserControllerSupport {

    // TODO: 팬 정보 관리는 추후 FanService로 분리 예정 — AuthService 책임 분리 (#83)
    private final AuthService authService;

    public FanController(AuthService authService, Environment environment) {
        super(environment);
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<FanResult>> getMyInfo(
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long localFanId) {
        Long fanId = resolveFanId(authentication, localFanId);
        return ResponseEntity.ok(ApiResponse.ok(authService.getMyInfo(fanId), traceId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<FanResult>> updateMyInfo(
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long localFanId,
            @RequestBody UpdateFanRequest request) {
        Long fanId = resolveFanId(authentication, localFanId);
        FanResult result = authService.updateMyInfo(
                new UpdateFanCommand(fanId, request.nickname(), request.allowNotification()));
        return ResponseEntity.ok(ApiResponse.ok(result, traceId()));
    }
}
