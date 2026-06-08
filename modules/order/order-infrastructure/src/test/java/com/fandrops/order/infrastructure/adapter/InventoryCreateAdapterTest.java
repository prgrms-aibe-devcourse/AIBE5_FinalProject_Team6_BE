package com.fandrops.order.infrastructure.adapter;

import com.fandrops.inventory.application.InventoryCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryCreateAdapter 단위 테스트")
class InventoryCreateAdapterTest {

    @Mock
    private InventoryCommandService inventoryCommandService;

    @InjectMocks
    private InventoryCreateAdapter sut;

    @Test
    @DisplayName("createInventory() — InventoryCommandService에 위임")
    void createInventory_delegates() {
        sut.createInventory(1L, 100);

        verify(inventoryCommandService).createInventory(1L, 100);
    }
}
