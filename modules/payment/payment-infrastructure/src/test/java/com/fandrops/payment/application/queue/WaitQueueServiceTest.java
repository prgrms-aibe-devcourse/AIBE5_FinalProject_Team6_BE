package com.fandrops.payment.application.queue;

import com.fandrops.payment.domain.queue.WaitQueueStatus;
import com.fandrops.payment.infrastructure.queue.LocalAccessTicketRepository;
import com.fandrops.payment.infrastructure.queue.LocalWaitQueueRepository;
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
        service          = new WaitQueueService(repository, ticketRepository, 600L, 10L);
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
        LocalAccessTicketRepository expiredTicketRepo = new LocalAccessTicketRepository(0L);
        WaitQueueService svc = new WaitQueueService(repository, expiredTicketRepo, 600L, 10L);

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

        service.exit(FAN_ID, PRODUCT_ID);

        QueueStatusResult status = service.getStatus(FAN_ID, PRODUCT_ID);
        assertThat(status.getStatus()).isEqualTo(WaitQueueStatus.PROCESSING.name());
    }

    @Test
    @DisplayName("expireTimeouts: threshold 초과 PROCESSING → EXPIRED 전이 및 토큰 무효화")
    void expireTimeouts_transitionsToExpiredAndInvalidatesToken() {
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));
        List<QueueAdvanceResult> advanced = service.advanceQueue(PRODUCT_ID, 1, 10);
        String token = advanced.get(0).getAccessToken();

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
        service.advanceQueue(PRODUCT_ID, 1, 10); // fan 1 → PROCESSING

        assertThat(service.getStatus(2L, PRODUCT_ID).getPosition()).isEqualTo(1L);
        assertThat(service.getStatus(3L, PRODUCT_ID).getPosition()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getActiveProductIds: WAITING 팬이 없으면 빈 집합 반환")
    void getActiveProductIds_emptyWhenNoWaiting() {
        assertThat(service.getActiveProductIds()).isEmpty();
    }

    @Test
    @DisplayName("getActiveProductIds: WAITING 팬이 있는 productId를 포함")
    void getActiveProductIds_includesProductWithWaiting() {
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));

        assertThat(service.getActiveProductIds()).containsExactly(PRODUCT_ID);
    }

    @Test
    @DisplayName("getActiveProductIds: 전원 PROCESSING 전이 후에도 제외 (WAITING 없으면 포함 안 됨)")
    void getActiveProductIds_excludesProductWithNoWaiting() {
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));
        service.advanceQueue(PRODUCT_ID, 1, 10); // FAN_ID → PROCESSING

        assertThat(service.getActiveProductIds()).isEmpty();
    }

    @Test
    @DisplayName("getActiveProductIds: 복수 productId 중 WAITING 보유 productId만 반환")
    void getActiveProductIds_returnsOnlyProductsWithWaiting() {
        Long otherProduct = 200L;
        service.join(new QueueJoinCommand(FAN_ID, PRODUCT_ID));       // PRODUCT_ID: WAITING
        service.join(new QueueJoinCommand(FAN_ID, otherProduct));     // otherProduct: WAITING
        service.advanceQueue(otherProduct, 1, 10);                    // otherProduct → PROCESSING

        assertThat(service.getActiveProductIds()).containsExactly(PRODUCT_ID);
    }
}