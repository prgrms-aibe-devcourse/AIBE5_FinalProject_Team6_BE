package com.fandrops.payment.infrastructure.queue;

import com.fandrops.payment.domain.queue.WaitQueueEntry;
import com.fandrops.payment.domain.queue.WaitQueueStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class RedisWaitQueueRepositoryTest {

    // CI: REDIS_HOST env var 주입 시 서비스 컨테이너 사용, 없으면 Testcontainers 기동
    private static GenericContainer<?> REDIS;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private RedisWaitQueueRepository repository;

    private static final Long   FAN_ID         = 1L;
    private static final Long   PRODUCT_ID     = 100L;
    private static final long   TTL            = 86400L;
    private static final String FAN_KEY_FORMAT = "queue:%d:fan:%d";

    @BeforeAll
    static void initFactory() {
        String host = System.getenv("REDIS_HOST");
        int port;
        if (host != null) {
            String portEnv = System.getenv("REDIS_PORT");
            port = portEnv != null ? Integer.parseInt(portEnv) : 6379;
        } else {
            REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
            REDIS.start();
            host = REDIS.getHost();
            port = REDIS.getMappedPort(6379);
        }
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void destroyFactory() {
        connectionFactory.destroy();
        if (REDIS != null) {
            REDIS.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // 순차 실행 기준 안전. @Execution(CONCURRENT) 전환 시 productId별 key prefix 삭제로 교체 필요
        redisTemplate.execute((RedisCallback<Object>) conn -> {
            conn.serverCommands().flushAll();
            return null;
        });
        repository = new RedisWaitQueueRepository(redisTemplate, TTL);
    }

    // ─── join ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("join: WAITING 상태 entry 생성, 순번 1")
    void join_createsWaitingEntry() {
        WaitQueueEntry entry = repository.join(FAN_ID, PRODUCT_ID);

        assertThat(entry.getStatus()).isEqualTo(WaitQueueStatus.WAITING);
        assertThat(entry.getPosition()).isEqualTo(1L);
        assertThat(entry.getFanId()).isEqualTo(FAN_ID);
        assertThat(entry.getProductId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    @DisplayName("join: 비-terminal 중복 요청 시 기존 entry 반환")
    void join_duplicateNonTerminal_returnsExistingEntry() {
        WaitQueueEntry first = repository.join(FAN_ID, PRODUCT_ID);
        WaitQueueEntry second = repository.join(FAN_ID, PRODUCT_ID);

        assertThat(second.getQueueId()).isEqualTo(first.getQueueId());
        assertThat(second.getStatus()).isEqualTo(WaitQueueStatus.WAITING);
    }

    @Test
    @DisplayName("join: DONE 후 재진입 시 새 WAITING entry 생성")
    void join_afterDone_createsNewEntry() {
        repository.join(FAN_ID, PRODUCT_ID);
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());
        repository.transitionToTerminal(FAN_ID, PRODUCT_ID, WaitQueueStatus.DONE);

        WaitQueueEntry rejoined = repository.join(FAN_ID, PRODUCT_ID);

        assertThat(rejoined.getStatus()).isEqualTo(WaitQueueStatus.WAITING);
        assertThat(rejoined.getPosition()).isEqualTo(1L);
    }

    @Test
    @DisplayName("join: EXPIRED 후 재진입 시 새 WAITING entry 생성")
    void join_afterExpired_createsNewEntry() {
        repository.join(FAN_ID, PRODUCT_ID);
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());
        repository.transitionToTerminal(FAN_ID, PRODUCT_ID, WaitQueueStatus.EXPIRED);

        WaitQueueEntry rejoined = repository.join(FAN_ID, PRODUCT_ID);

        assertThat(rejoined.getStatus()).isEqualTo(WaitQueueStatus.WAITING);
    }

    @Test
    @DisplayName("join: fanKey에 TTL이 설정됨")
    void join_setsKeyTtl() {
        repository.join(FAN_ID, PRODUCT_ID);

        Long ttl = redisTemplate.getExpire(
                String.format(FAN_KEY_FORMAT, PRODUCT_ID, FAN_ID), TimeUnit.SECONDS);

        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(TTL);
    }

    // ─── findEntry / getPosition ──────────────────────────────────────────────

    @Test
    @DisplayName("findEntry: 존재하지 않는 팬 → Optional.empty()")
    void findEntry_nonExistent_returnsEmpty() {
        Optional<WaitQueueEntry> result = repository.findEntry(999L, PRODUCT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getPosition: 다수 팬 등록 시 등록 순서대로 순번 부여")
    void getPosition_multipleWaiting_returnsCorrectRank() {
        for (long i = 1; i <= 3; i++) {
            repository.join(i, PRODUCT_ID);
        }

        assertThat(repository.getPosition(1L, PRODUCT_ID)).isEqualTo(1L);
        assertThat(repository.getPosition(2L, PRODUCT_ID)).isEqualTo(2L);
        assertThat(repository.getPosition(3L, PRODUCT_ID)).isEqualTo(3L);
    }

    // ─── exit ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("exit: WAITING entry 제거 후 findEntry empty")
    void exit_removesWaitingEntry() {
        repository.join(FAN_ID, PRODUCT_ID);

        repository.exit(FAN_ID, PRODUCT_ID);

        assertThat(repository.findEntry(FAN_ID, PRODUCT_ID)).isEmpty();
    }

    @Test
    @DisplayName("exit: PROCESSING 상태에서 호출 시 무시 — entry 유지")
    void exit_ignoredWhenProcessing() {
        repository.join(FAN_ID, PRODUCT_ID);
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());

        repository.exit(FAN_ID, PRODUCT_ID);

        Optional<WaitQueueEntry> entry = repository.findEntry(FAN_ID, PRODUCT_ID);
        assertThat(entry).isPresent();
        assertThat(entry.get().getStatus()).isEqualTo(WaitQueueStatus.PROCESSING);
    }

    @Test
    @DisplayName("exit: DONE 상태에서 호출 시 무시 — entry 유지")
    void exit_ignoredWhenDone() {
        repository.join(FAN_ID, PRODUCT_ID);
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());
        repository.transitionToTerminal(FAN_ID, PRODUCT_ID, WaitQueueStatus.DONE);

        repository.exit(FAN_ID, PRODUCT_ID);

        assertThat(repository.findEntry(FAN_ID, PRODUCT_ID))
                .isPresent()
                .get()
                .extracting(WaitQueueEntry::getStatus)
                .isEqualTo(WaitQueueStatus.DONE);
    }

    @Test
    @DisplayName("exit: EXPIRED 상태에서 호출 시 무시 — entry 유지")
    void exit_ignoredWhenExpired() {
        repository.join(FAN_ID, PRODUCT_ID);
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());
        repository.transitionToTerminal(FAN_ID, PRODUCT_ID, WaitQueueStatus.EXPIRED);

        repository.exit(FAN_ID, PRODUCT_ID);

        assertThat(repository.findEntry(FAN_ID, PRODUCT_ID))
                .isPresent()
                .get()
                .extracting(WaitQueueEntry::getStatus)
                .isEqualTo(WaitQueueStatus.EXPIRED);
    }

    // ─── transitionToProcessing ───────────────────────────────────────────────

    @Test
    @DisplayName("transitionToProcessing: WAITING → PROCESSING 전이 성공, countProcessing 증가")
    void transitionToProcessing_success() {
        repository.join(FAN_ID, PRODUCT_ID);

        boolean result = repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());

        assertThat(result).isTrue();
        assertThat(repository.findEntry(FAN_ID, PRODUCT_ID))
                .isPresent()
                .get()
                .extracting(WaitQueueEntry::getStatus)
                .isEqualTo(WaitQueueStatus.PROCESSING);
        assertThat(repository.countProcessing(PRODUCT_ID)).isEqualTo(1L);
    }

    @Test
    @DisplayName("transitionToProcessing: 이미 PROCESSING 상태에서 재호출 시 false")
    void transitionToProcessing_alreadyProcessing_returnsFalse() {
        repository.join(FAN_ID, PRODUCT_ID);
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());

        boolean second = repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());

        assertThat(second).isFalse();
        assertThat(repository.countProcessing(PRODUCT_ID)).isEqualTo(1L);
    }

    @Test
    @DisplayName("transitionToProcessing: 10 스레드 동시 호출 시 정확히 1번만 PROCESSING 전이")
    void transitionToProcessing_concurrent_onlyOneSucceeds() throws InterruptedException {
        repository.join(FAN_ID, PRODUCT_ID);

        int threads = 10;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now())) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        // timeout 시 스레드가 아직 실행 중인 상태에서 assert가 실행되지 않도록 강제 종료
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            fail("동시성 테스트 타임아웃 — CI 환경에서 스레드가 5초 내 완료되지 않음");
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(repository.countProcessing(PRODUCT_ID)).isEqualTo(1L);
    }

    // ─── transitionToTerminal ─────────────────────────────────────────────────

    @Test
    @DisplayName("transitionToTerminal: PROCESSING → DONE 전이")
    void transitionToTerminal_done() {
        repository.join(FAN_ID, PRODUCT_ID);
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());

        repository.transitionToTerminal(FAN_ID, PRODUCT_ID, WaitQueueStatus.DONE);

        assertThat(repository.findEntry(FAN_ID, PRODUCT_ID))
                .isPresent()
                .get()
                .extracting(WaitQueueEntry::getStatus)
                .isEqualTo(WaitQueueStatus.DONE);
        assertThat(repository.countProcessing(PRODUCT_ID)).isEqualTo(0L);
    }

    @Test
    @DisplayName("transitionToTerminal: PROCESSING → EXPIRED 전이")
    void transitionToTerminal_expired() {
        repository.join(FAN_ID, PRODUCT_ID);
        repository.transitionToProcessing(FAN_ID, PRODUCT_ID, Instant.now());

        repository.transitionToTerminal(FAN_ID, PRODUCT_ID, WaitQueueStatus.EXPIRED);

        assertThat(repository.findEntry(FAN_ID, PRODUCT_ID))
                .isPresent()
                .get()
                .extracting(WaitQueueEntry::getStatus)
                .isEqualTo(WaitQueueStatus.EXPIRED);
        assertThat(repository.countProcessing(PRODUCT_ID)).isEqualTo(0L);
    }

    @Test
    @DisplayName("transitionToTerminal: WAITING 상태에서 호출 시 무시 — entry 유지")
    void transitionToTerminal_ignoredWhenNotProcessing() {
        repository.join(FAN_ID, PRODUCT_ID);

        repository.transitionToTerminal(FAN_ID, PRODUCT_ID, WaitQueueStatus.DONE);

        assertThat(repository.findEntry(FAN_ID, PRODUCT_ID))
                .isPresent()
                .get()
                .extracting(WaitQueueEntry::getStatus)
                .isEqualTo(WaitQueueStatus.WAITING);
    }

    // ─── findProcessingExpiredFanIds / countProcessing ────────────────────────

    @Test
    @DisplayName("findProcessingExpiredFanIds: processingStartAt score 기준 threshold 이전 팬만 반환")
    void findProcessingExpiredFanIds_returnsOnlyExpired() {
        repository.join(1L, PRODUCT_ID);
        repository.join(2L, PRODUCT_ID);

        Instant old    = Instant.now().minusSeconds(600);
        Instant recent = Instant.now();
        repository.transitionToProcessing(1L, PRODUCT_ID, old);
        repository.transitionToProcessing(2L, PRODUCT_ID, recent);

        Instant threshold = Instant.now().minusSeconds(300);
        List<Long> expired = repository.findProcessingExpiredFanIds(PRODUCT_ID, threshold);

        assertThat(expired).containsExactly(1L);
    }

    @Test
    @DisplayName("countProcessing: PROCESSING 상태 팬 수 정확 반환")
    void countProcessing_accurate() {
        for (long i = 1; i <= 3; i++) {
            repository.join(i, PRODUCT_ID);
            repository.transitionToProcessing(i, PRODUCT_ID, Instant.now());
        }

        assertThat(repository.countProcessing(PRODUCT_ID)).isEqualTo(3L);
    }

    // ─── findTopWaitingFanIds ─────────────────────────────────────────────────

    @Test
    @DisplayName("findTopWaitingFanIds: 등록 순서(joinedAt score) 기준 limit 개수만 반환")
    void findTopWaitingFanIds_returnsInJoinOrder() {
        for (long i = 1; i <= 5; i++) {
            repository.join(i, PRODUCT_ID);
        }

        List<Long> top3 = repository.findTopWaitingFanIds(PRODUCT_ID, 3);

        assertThat(top3).hasSize(3).containsExactly(1L, 2L, 3L);
    }
}