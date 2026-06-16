package com.fandrops.inventory.infrastructure.persistence;

import com.fandrops.inventory.domain.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("재고 atomic UPDATE 동시성 통합 테스트")
class InventoryReserveConcurrencyTest {

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    private static final Long PRODUCT_ID = 9001L;

    @AfterEach
    void cleanup() {
        inventoryJpaRepository.findByProductId(PRODUCT_ID)
                .ifPresent(inventoryJpaRepository::delete);
    }

    @Test
    @DisplayName("재고 10개에 20개 동시 예약 요청 시 정확히 10개만 성공하고 오버셀 없음")
    void reserve_concurrent20threads_stock10_exactlyTenSucceed() throws InterruptedException {
        // given
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.execute(status -> {
            inventoryJpaRepository.saveAndFlush(
                    InventoryJpaEntity.from(Inventory.create(PRODUCT_ID, 10)));
            return null;
        });

        int threadCount = 20;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when — 20개 스레드가 동시에 재고 1개씩 예약
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Integer affected = txTemplate.execute(
                            status -> inventoryJpaRepository.reserveAtomic(PRODUCT_ID, 1));
                    if (Integer.valueOf(1).equals(affected)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // 실패는 성공 카운트에 포함하지 않음
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // then — 오버셀 0건: 성공 건수 == 초기 재고
        assertThat(successCount.get()).isEqualTo(10);

        InventoryJpaEntity finalEntity = inventoryJpaRepository.findByProductId(PRODUCT_ID)
                .orElseThrow();
        Inventory finalState = finalEntity.toDomain();
        assertThat(finalState.getReservedQty()).isEqualTo(10);
        assertThat(finalState.getAvailableQty()).isEqualTo(0);
    }
}
