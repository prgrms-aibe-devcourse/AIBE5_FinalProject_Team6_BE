package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.UpdateFanRequest;
import com.fandrops.user.application.dto.FanResult;
import com.fandrops.user.application.dto.UpdateFanCommand;
import com.fandrops.user.application.service.FanService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fans")
public class FanController extends UserControllerSupport {

    private final FanService fanService;

    public FanController(FanService fanService, Environment environment) {
        super(environment);
        this.fanService = fanService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<FanResult>> getMyInfo(
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long localFanId) {
        Long fanId = resolveFanId(authentication, localFanId);
        return ResponseEntity.ok(ApiResponse.ok(fanService.getMyInfo(fanId), traceId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<FanResult>> updateMyInfo(
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long localFanId,
            @RequestBody UpdateFanRequest request) {
        Long fanId = resolveFanId(authentication, localFanId);
        FanResult result = fanService.updateMyInfo(
                new UpdateFanCommand(fanId, request.nickname(), request.allowNotification()));
        return ResponseEntity.ok(ApiResponse.ok(result, traceId()));
    }
}
