package com.fandrops.payment.infrastructure.queue;

import com.fandrops.payment.application.queue.QueueAdvanceResult;
import com.fandrops.payment.application.queue.QueueJoinCommand;
import com.fandrops.payment.application.queue.QueueJoinResult;
import com.fandrops.payment.application.queue.QueueStatusResult;
import com.fandrops.payment.application.queue.WaitQueueService;
import com.fandrops.payment.domain.queue.WaitQueueStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitQueueServiceTest {

    private LocalWaitQueueRepository repository;
    private LocalAccessTicketRepository ticketRepository;
    private WaitQueueService service;

    private static final Long FAN_ID     = 1L;
    private static final Long PRODUCT_ID = 100L;

    @BeforeEach
    void setUp() {
        repository       = new LocalWaitQueueRepository();
        ticketRepository = new LocalAccessTicketRepository(300L);
        service          = new WaitQueueService(repository, ticketRepository);
    }

    @Test
    @DisplayName("join: WAITING 상태로 대기열 등록")
    void join_createsWaitingEntry() {
        QueueJoinResult result = service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));

        assertThat(result.getStatus()).isEqualTo(WaitQueueStatus.WAITING.name());
        assertThat(result.getPosition()).isEqualTo(1L);
    }

    @Test
    @DisplayName("W-1: DONE은 Terminal — 재등록 시 새 WAITING entry 생성")
    void w1_doneIsTerminal_rejoinCreatesNewEntry() {
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());
        repository.transitionToTerminal(FAN_ID, PRODUCT_ID, WaitQueueStatus.DONE);

        QueueJoinResult rejoined = service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));

        assertThat(rejoined.getStatus()).isEqualTo(WaitQueueStatus.WAITING.name());
        assertThat(rejoined.getPosition()).isEqualTo(1L);
    }

    @Test
    @DisplayName("W-1: EXPIRED는 Terminal — 재등록 시 새 WAITING entry 생성")
    void w1_expiredIsTerminal_rejoinCreatesNewEntry() {
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());
        repository.transitionToTerminal(FAN_ID, PRODUCT_ID, WaitQueueStatus.EXPIRED);

        QueueJoinResult rejoined = service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));

        assertThat(rejoined.getStatus()).isEqualTo(WaitQueueStatus.WAITING.name());
    }

    @Test
    @DisplayName("W-2: DONE 전이 후 대기열 상태는 DONE (주문 성공 여부는 ORDER.status 별도)")
    void w2_doneTransition_independentOfOrderStatus() {
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());
        repository.transitionToTerminal(FAN_ID, PRODUCT_ID, WaitQueueStatus.DONE);

        QueueStatusResult status = service.getStatus(FAN_ID, PRODUCT_ID);

        // 대기열 DONE은 주문 성공을 의미하지 않는다 — ORDER.status가 유일한 판단 기준 (W-2)
        assertThat(status.getStatus()).isEqualTo(WaitQueueStatus.DONE.name());
    }

    @Test
    @DisplayName("W-3: advanceQueue는 fanId × productId 당 Access Ticket 1개만 유효")
    void w3_advanceQueue_issuesOneTokenPerFanAndProduct() {
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));

        List<QueueAdvanceResult> first = service.advanceQueue(PRODUCT_ID, 5, 10);
        assertThat(first).hasSize(1);
        String token = first.get(0).getAccessToken();

        // PROCESSING 상태 → 재진입 시도 → 빈 결과
        List<QueueAdvanceResult> second = service.advanceQueue(PRODUCT_ID, 5, 10);
        assertThat(second).isEmpty();

        // 기존 토큰은 유효 유지
        assertThat(ticketRepository.isValid(token, FAN_ID, PRODUCT_ID)).isTrue();
    }

    @Test
    @DisplayName("W-3: maxConcurrent 초과 시 추가 PROCESSING 진입 차단")
    void w3_advanceQueue_blocksWhenMaxConcurrentReached() {
        for (long i = 1; i <= 3; i++) {
            service.join(new QueueJoinCommand(i, PRODUCT_ID));
        }

        service.advanceQueue(PRODUCT_ID, 5, 2); // 2명 진입
        List<QueueAdvanceResult> blocked = service.advanceQueue(PRODUCT_ID, 5, 2);

        assertThat(blocked).isEmpty();
    }

    @Test
    @DisplayName("Q-1: 만료된 Access Ticket은 isValid false 반환")
    void q1_expiredAccessTicket_isInvalid() {
        // TTL=0 으로 즉시 만료 토큰 발급
        LocalAccessTicketRepository expiredTicketRepo = new LocalAccessTicketRepository(0L);
        WaitQueueService svc = new WaitQueueService(repository, expiredTicketRepo);

        svc.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));
        List<QueueAdvanceResult> advanced = svc.advanceQueue(PRODUCT_ID, 1, 10);
        String token = advanced.get(0).getAccessToken();

        assertThat(expiredTicketRepo.isValid(token, FAN_ID, PRODUCT_ID)).isFalse();
    }

    @Test
    @DisplayName("exit: WAITING entry를 대기열에서 제거")
    void exit_removesWaitingEntry() {
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));

        service.exit(FAN_ID, PRODUCT_ID);

        assertThatThrownBy(() -> service.getStatus(FAN_ID, PRODUCT_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("exit: PROCESSING 상태에서 호출해도 무시")
    void exit_ignoredWhenProcessing() {
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));
        service.advanceQueue(PRODUCT_ID, 1, 10);

        service.exit(FAN_ID, PRODUCT_ID); // 무시

        QueueStatusResult status = service.getStatus(FAN_ID, PRODUCT_ID);
        assertThat(status.getStatus()).isEqualTo(WaitQueueStatus.PROCESSING.name());
    }

    @Test
    @DisplayName("expireTimeouts: threshold 초과 PROCESSING → EXPIRED 전이 및 토큰 무효화")
    void expireTimeouts_transitionsToExpiredAndInvalidatesToken() {
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));
        List<QueueAdvanceResult> advanced = service.advanceQueue(PRODUCT_ID, 1, 10);
        String token = advanced.get(0).getAccessToken();

        // threshold를 미래로 설정 → 현재 processingStartAt보다 크므로 만료 대상
        List<Long> expired = service.expireTimeouts(PRODUCT_ID, Instant.now().plusSeconds(60));

        assertThat(expired).containsExactly(FAN_ID);
        assertThat(ticketRepository.isValid(token, FAN_ID, PRODUCT_ID)).isFalse();
        assertThat(service.getStatus(FAN_ID, PRODUCT_ID).getStatus())
                .isEqualTo(WaitQueueStatus.EXPIRED.name());
    }

    @Test
    @DisplayName("순번: 다수 팬 대기 시 WAITING 항목만 순번 계산")
    void position_countOnlyWaitingEntries() {
        for (long i = 1; i <= 3; i++) {
            service.join(new QueueJoinCommand(i, PRODUCT_ID));
        }
        // fan 1을 PROCESSING으로 전이 → fan 2가 1번, fan 3이 2번
        service.advanceQueue(PRODUCT_ID, 1, 10);

        assertThat(service.getStatus(2L, PRODUCT_ID).getPosition()).isEqualTo(1L);
        assertThat(service.getStatus(3L, PRODUCT_ID).getPosition()).isEqualTo(2L);
    }
}