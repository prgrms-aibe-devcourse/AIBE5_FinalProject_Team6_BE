package com.fandrops.payment.api.queue;

import com.fandrops.payment.application.queue.QueueJoinCommand;
import com.fandrops.payment.application.queue.QueueJoinResult;
import com.fandrops.payment.application.queue.QueueStatusResult;
import com.fandrops.payment.application.queue.WaitQueueService;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/queue")
public class WaitQueueController {

    private final WaitQueueService waitQueueService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final Environment environment;

    public WaitQueueController(WaitQueueService waitQueueService,
                               SseEmitterRegistry sseEmitterRegistry,
                               Environment environment) {
        this.waitQueueService = waitQueueService;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.environment = environment;
    }

    /**
     * 대기열 등록. 이미 활성 entry가 있으면 현재 entry를 반환한다.
     * fanId는 JWT 클레임 기준. 로컬 테스트 시 X-Fan-Id 헤더로 대체한다.
     */
    @PostMapping("/join/{productId}")
    public ResponseEntity<QueueJoinResponse> join(
            @PathVariable Long productId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        QueueJoinResult result = waitQueueService.join(new QueueJoinCommand(fanId, productId));

        return ResponseEntity.ok(new QueueJoinResponse(
                result.getQueueId(),
                result.getPosition(),
                ApiQueueStatus.from(result.getStatus())
        ));
    }

    /**
     * 현재 순번·상태·예상 대기 시간 조회 (Polling용).
     */
    @GetMapping("/status")
    public ResponseEntity<QueueStatusResponse> status(
            @RequestParam Long productId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        QueueStatusResult result = waitQueueService.getStatus(fanId, productId);

        return ResponseEntity.ok(new QueueStatusResponse(
                result.getPosition(),
                ApiQueueStatus.from(result.getStatus()),
                result.getEstimatedWaitSec()
        ));
    }

    /**
     * 실시간 순번·상태 SSE 스트림. 연결 즉시 현재 상태를 전송하고, 이후 변경 시 서버에서 푸시한다.
     * PROCESSING 전이 시 accessToken 포함. 연결 타임아웃: 60s (클라이언트가 재연결).
     */
    @GetMapping(value = "/stream/{productId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable Long productId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        SseEmitter emitter = sseEmitterRegistry.registerOrReject(productId, fanId);

        // 연결 직후 현재 상태를 즉시 전송
        try {
            QueueStatusResult current = waitQueueService.getStatus(fanId, productId);
            sseEmitterRegistry.sendToFan(productId, fanId,
                    QueueStreamEvent.waiting(current.getPosition(), current.getEstimatedWaitSec()));
        } catch (Exception ignored) {
            // 대기열에 없는 경우 스케줄러 첫 tick에서 처리
        }

        return emitter;
    }

    /**
     * 대기열 이탈. WAITING 상태에서만 유효하며, PROCESSING 이후에는 무시된다.
     */
    @DeleteMapping("/exit/{productId}")
    public ResponseEntity<Void> exit(
            @PathVariable Long productId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        waitQueueService.exit(fanId, productId);
        return ResponseEntity.noContent().build();
    }

    private Long resolveFanId(Authentication authentication, Long fanIdHeader) {
        if (fanIdHeader != null && isLocalProfile()) {
            return fanIdHeader;
        }
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long fanId) {
            return fanId;
        }
        throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }
}
