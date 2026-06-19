package com.fandrops.inventory.application;

import com.fandrops.inventory.application.dto.InventoryHistoryListResponse;
import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistorySummary;
import com.fandrops.inventory.domain.InventoryRefType;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryQueryService 단위 테스트")
class InventoryQueryServiceTest {

    @Mock
    private InventoryHistoryRepository inventoryHistoryRepository;

    @InjectMocks
    private InventoryQueryService sut;

    private static final Long AGENCY_ID = 1L;
    private static final Long ARTIST_ID = 10L;
    private static final Long PRODUCT_ID = 100L;

    private InventoryHistorySummary makeSummary(Long id) {
        return new InventoryHistorySummary(id, 1L, InventoryChangeType.DECREASE,
                1, 10, 9, 1L, InventoryRefType.ORDER, LocalDateTime.now(),
                PRODUCT_ID, "테스트 상품");
    }

    @Nested
    @DisplayName("getAgencyInventoryHistory()")
    class GetAgencyInventoryHistory {

        @Test
        @DisplayName("결과가 size와 같으면 nextCursor = 마지막 historyId")
        void returnsNextCursorWhenFullPage() {
            List<InventoryHistorySummary> summaries = List.of(makeSummary(5L), makeSummary(3L), makeSummary(1L));
            given(inventoryHistoryRepository.findAgencySummaries(AGENCY_ID, ARTIST_ID, PRODUCT_ID, null, 3))
                    .willReturn(summaries);

            InventoryHistoryListResponse response =
                    sut.getAgencyInventoryHistory(AGENCY_ID, ARTIST_ID, PRODUCT_ID, null, 3);

            assertEquals(3, response.items().size());
            assertEquals(1L, response.nextCursor());
        }

        @Test
        @DisplayName("결과가 size보다 작으면 nextCursor = null (마지막 페이지)")
        void returnsNullCursorWhenLastPage() {
            List<InventoryHistorySummary> summaries = List.of(makeSummary(5L), makeSummary(3L));
            given(inventoryHistoryRepository.findAgencySummaries(AGENCY_ID, null, null, null, 3))
                    .willReturn(summaries);

            InventoryHistoryListResponse response =
                    sut.getAgencyInventoryHistory(AGENCY_ID, null, null, null, 3);

            assertEquals(2, response.items().size());
            assertNull(response.nextCursor());
        }

        @Test
        @DisplayName("결과가 없으면 빈 items와 nextCursor = null")
        void returnsEmptyWhenNoResults() {
            given(inventoryHistoryRepository.findAgencySummaries(AGENCY_ID, ARTIST_ID, PRODUCT_ID, null, 20))
                    .willReturn(List.of());

            InventoryHistoryListResponse response =
                    sut.getAgencyInventoryHistory(AGENCY_ID, ARTIST_ID, PRODUCT_ID, null, 20);

            assertEquals(0, response.items().size());
            assertNull(response.nextCursor());
        }

        @Test
        @DisplayName("InventoryHistoryItem 필드가 InventoryHistorySummary로부터 올바르게 매핑된다")
        void itemFieldsMappedCorrectly() {
            LocalDateTime changedAt = LocalDateTime.of(2026, 6, 19, 12, 0, 0);
            InventoryHistorySummary summary = new InventoryHistorySummary(
                    7L, 2L, InventoryChangeType.RESERVE, 5, 100, 95,
                    99L, InventoryRefType.ORDER, changedAt, 200L, "한정판 굿즈");
            given(inventoryHistoryRepository.findAgencySummaries(AGENCY_ID, null, null, null, 20))
                    .willReturn(List.of(summary));

            InventoryHistoryListResponse response =
                    sut.getAgencyInventoryHistory(AGENCY_ID, null, null, null, 20);

            var item = response.items().get(0);
            assertEquals(7L, item.historyId());
            assertEquals(2L, item.inventoryId());
            assertEquals("RESERVE", item.changeType());
            assertEquals(5, item.deltaQty());
            assertEquals(100, item.qtyBefore());
            assertEquals(95, item.qtyAfter());
            assertEquals(99L, item.referenceId());
            assertEquals("ORDER", item.refType());
            assertEquals(changedAt, item.changedAt());
            assertEquals(200L, item.productId());
            assertEquals("한정판 굿즈", item.productName());
        }
    }
}
