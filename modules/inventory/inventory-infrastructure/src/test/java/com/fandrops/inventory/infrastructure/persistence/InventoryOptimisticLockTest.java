package com.fandrops.inventory.infrastructure.persistence;

import com.fandrops.inventory.domain.Inventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@DisplayName("Inventory 낙관적 잠금 통합 테스트")
class InventoryOptimisticLockTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Test
    @DisplayName("stale 버전으로 save 시 ObjectOptimisticLockingFailureException 발생 - 단일 스레드 시뮬레이션")
    void save_withStaleVersion_throwsOptimisticLockException() {
        // given - 초기 저장 (DB version=0)
        InventoryJpaEntity initial = InventoryJpaEntity.from(Inventory.create(1L, 100));
        em.persistAndFlush(initial);
        Long id = initial.getId();
        em.clear();

        // stale 스냅샷: version=0인 도메인 객체 직접 생성
        Inventory staleDomain = Inventory.reconstitute(id, 1L, 100, 0, 100, 0);

        // 첫 번째 저장으로 DB version → 1 (다른 트랜잭션이 먼저 커밋한 상황 시뮬레이션)
        InventoryJpaEntity current = inventoryJpaRepository.findById(id).orElseThrow();
        Inventory currentDomain = current.toDomain();
        currentDomain.reserve(10, 1L);
        inventoryJpaRepository.saveAndFlush(InventoryJpaEntity.from(currentDomain));
        em.clear();

        // when - stale 버전(0)으로 저장 시도
        staleDomain.reserve(5, 2L);
        InventoryJpaEntity staleEntity = InventoryJpaEntity.from(staleDomain);

        // then - 버전 불일치로 예외 발생
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> inventoryJpaRepository.saveAndFlush(staleEntity));
    }
}
