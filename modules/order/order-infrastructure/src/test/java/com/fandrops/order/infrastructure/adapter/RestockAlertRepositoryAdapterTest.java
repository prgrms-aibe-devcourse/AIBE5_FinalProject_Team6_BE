package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.RestockAlert;
import com.fandrops.order.domain.RestockAlertStatus;
import com.fandrops.order.infrastructure.persistence.RestockAlertJpaEntity;
import com.fandrops.order.infrastructure.persistence.RestockAlertJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestockAlertRepositoryAdapter 단위 테스트")
class RestockAlertRepositoryAdapterTest {

    @Mock
    private RestockAlertJpaRepository jpaRepository;

    @InjectMocks
    private RestockAlertRepositoryAdapter sut;

    private static final Long FAN_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long ALERT_ID = 100L;

    private RestockAlert pendingDomain() {
        return RestockAlert.of(ALERT_ID, FAN_ID, PRODUCT_ID, RestockAlertStatus.PENDING, LocalDateTime.now());
    }

    private RestockAlertJpaEntity pendingEntity() {
        return RestockAlertJpaEntity.fromWithId(pendingDomain());
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("id 없는 신규 알림 — INSERT 경로")
        void save_newAlert() {
            RestockAlert newAlert = RestockAlert.create(FAN_ID, PRODUCT_ID);
            given(jpaRepository.save(any())).willReturn(pendingEntity());

            RestockAlert result = sut.save(newAlert);

            assertNotNull(result);
            assertEquals(ALERT_ID, result.getId());
        }

        @Test
        @DisplayName("id 있는 기존 알림 — UPDATE 경로")
        void save_existingAlert() {
            given(jpaRepository.save(any())).willReturn(pendingEntity());

            RestockAlert result = sut.save(pendingDomain());

            assertEquals(ALERT_ID, result.getId());
        }
    }

    @Nested
    @DisplayName("findPendingByFanIdAndProductId()")
    class FindPending {

        @Test
        @DisplayName("PENDING 알림 존재 시 Optional<RestockAlert> 반환")
        void findPending_found() {
            given(jpaRepository.findByFanIdAndProductIdAndStatus(FAN_ID, PRODUCT_ID, RestockAlertStatus.PENDING))
                    .willReturn(Optional.of(pendingEntity()));

            Optional<RestockAlert> result = sut.findPendingByFanIdAndProductId(FAN_ID, PRODUCT_ID);

            assertTrue(result.isPresent());
            assertEquals(ALERT_ID, result.get().getId());
        }

        @Test
        @DisplayName("PENDING 없으면 Optional.empty()")
        void findPending_notFound() {
            given(jpaRepository.findByFanIdAndProductIdAndStatus(FAN_ID, PRODUCT_ID, RestockAlertStatus.PENDING))
                    .willReturn(Optional.empty());

            assertTrue(sut.findPendingByFanIdAndProductId(FAN_ID, PRODUCT_ID).isEmpty());
        }
    }

    @Nested
    @DisplayName("findAllPendingByProductId()")
    class FindAllPending {

        @Test
        @DisplayName("PENDING 목록 반환")
        void findAllPending_returnsList() {
            given(jpaRepository.findAllByProductIdAndStatus(PRODUCT_ID, RestockAlertStatus.PENDING))
                    .willReturn(List.of(pendingEntity()));

            List<RestockAlert> result = sut.findAllPendingByProductId(PRODUCT_ID);

            assertEquals(1, result.size());
            assertEquals(ALERT_ID, result.get(0).getId());
        }
    }
}
