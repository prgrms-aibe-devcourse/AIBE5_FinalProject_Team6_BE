package com.fandrops.payment.api.queue;

import com.fandrops.payment.application.queue.QueueJoinCommand;
import com.fandrops.payment.application.queue.QueueJoinResult;
import com.fandrops.payment.application.queue.QueueStatusResult;
import com.fandrops.payment.application.queue.WaitQueueService;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/queue")
public class WaitQueueController {

    private final WaitQueueService waitQueueService;
    private final Environment environment;

    public WaitQueueController(WaitQueueService waitQueueService, Environment environment) {
        this.waitQueueService = waitQueueService;
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

    // TODO: user 모듈 Auth 계약 확정 후 JWT 클레임에서 fanId 추출로 교체 (표지민 협의)
    private Long resolveFanId(Authentication authentication, Long fanIdHeader) {
        if (fanIdHeader != null && isLocalProfile()) {
            return fanIdHeader;
        }
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return Long.parseLong(authentication.getName());
        }
        throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }
}
