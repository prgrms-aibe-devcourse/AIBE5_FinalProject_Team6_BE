package com.fandrops.order.infrastructure.adapter;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.order.domain.InventoryInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReadAdapter 단위 테스트")
class InventoryReadAdapterTest {

    @Mock
    private InventoryCommandService inventoryCommandService;

    @InjectMocks
    private InventoryReadAdapter sut;

    @Test
    @DisplayName("getByProductId() — Inventory → InventoryInfo 변환 정상")
    void getByProductId_mapsToInventoryInfo() {
        Inventory inventory = Inventory.reconstitute(1L, 10L, 100, 10, 90, 0);
        given(inventoryCommandService.getInventoryByProductId(10L)).willReturn(inventory);

        InventoryInfo result = sut.getByProductId(10L);

        assertEquals(100, result.getTotalQty());
        assertEquals(10, result.getReservedQty());
        assertEquals(90, result.getAvailableQty());
    }
}
