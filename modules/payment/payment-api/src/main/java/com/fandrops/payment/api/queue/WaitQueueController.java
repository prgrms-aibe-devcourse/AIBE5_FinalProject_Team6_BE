package com.fandrops.payment.api.queue;

import com.fandrops.payment.application.queue.QueueJoinCommand;
import com.fandrops.payment.application.queue.QueueStatusResult;
import com.fandrops.payment.application.queue.WaitQueueService;
import com.fandrops.payment.domain.queue.WaitQueueEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/queue")
public class WaitQueueController {

    private final WaitQueueService waitQueueService;

    public WaitQueueController(WaitQueueService waitQueueService) {
        this.waitQueueService = waitQueueService;
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
        WaitQueueEntry entry = waitQueueService.join(new QueueJoinCommand(fanId, productId));

        return ResponseEntity.ok(new QueueJoinResponse(
                entry.getQueueId(),
                entry.getPosition(),
                entry.getStatus()
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
                result.getStatus(),
                result.getEstimatedWaitSec()
        ));
    }

    // TODO: user 모듈 Auth 계약 확정 후 JWT 클레임에서 fanId 추출로 교체 (표지민 협의)
    private Long resolveFanId(Authentication authentication, Long fanIdHeader) {
        if (fanIdHeader != null) {
            return fanIdHeader;
        }
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return Long.parseLong(authentication.getName());
        }
        throw new IllegalArgumentException("인증 정보가 없습니다. X-Fan-Id 헤더 또는 Bearer 토큰을 제공하세요.");
    }
}