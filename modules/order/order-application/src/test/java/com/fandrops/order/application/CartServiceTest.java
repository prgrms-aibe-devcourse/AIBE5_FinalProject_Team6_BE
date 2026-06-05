package com.fandrops.order.application;

import com.fandrops.order.application.dto.AddCartItemCommand;
import com.fandrops.order.application.dto.CartItemResponse;
import com.fandrops.order.application.dto.CartResponse;
import com.fandrops.order.application.dto.UpdateCartItemCommand;
import com.fandrops.order.domain.Cart;
import com.fandrops.order.domain.CartItem;
import com.fandrops.order.domain.exception.CartAccessDeniedException;
import com.fandrops.order.domain.exception.CartItemNotFoundException;
import com.fandrops.order.domain.port.CartItemRepository;
import com.fandrops.order.domain.port.CartRepository;
import com.fandrops.order.domain.port.ProductPricePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 단위 테스트")
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductPricePort productPricePort;

    @InjectMocks
    private CartService sut;

    private static final Long FAN_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long CART_ID = 100L;
    private static final Long CART_ITEM_ID = 1000L;

    private Cart cart() {
        return Cart.of(CART_ID, FAN_ID, LocalDateTime.now(), LocalDateTime.now());
    }

    private CartItem cartItem(int quantity) {
        return CartItem.of(CART_ITEM_ID, CART_ID, PRODUCT_ID, quantity, LocalDateTime.now());
    }

    @Nested
    @DisplayName("getCart()")
    class GetCart {

        @Test
        @DisplayName("장바구니 없으면 빈 items 반환")
        void getCart_noCart_returnsEmpty() {
            given(cartRepository.findByFanId(FAN_ID)).willReturn(Optional.empty());

            CartResponse result = sut.getCart(FAN_ID);

            assertTrue(result.getItems().isEmpty());
        }

        @Test
        @DisplayName("장바구니 있으면 items + 가격 조합 반환")
        void getCart_withItems_returnsCartResponse() {
            CartItem item = cartItem(2);
            given(cartRepository.findByFanId(FAN_ID)).willReturn(Optional.of(cart()));
            given(cartItemRepository.findAllByCartId(CART_ID)).willReturn(List.of(item));
            given(productPricePort.getPrice(PRODUCT_ID)).willReturn(BigDecimal.valueOf(10000));

            CartResponse result = sut.getCart(FAN_ID);

            assertEquals(1, result.getItems().size());
            CartItemResponse response = result.getItems().get(0);
            assertEquals(CART_ITEM_ID, response.getCartItemId());
            assertEquals(PRODUCT_ID, response.getProductId());
            assertEquals(2, response.getQuantity());
            assertEquals(BigDecimal.valueOf(10000), response.getPrice());
        }
    }

    @Nested
    @DisplayName("addItem()")
    class AddItem {

        @Test
        @DisplayName("장바구니 없으면 생성 후 신규 아이템 추가")
        void addItem_noCart_createsCartAndItem() {
            Cart savedCart = cart();
            CartItem savedItem = cartItem(3);
            given(cartRepository.findByFanId(FAN_ID)).willReturn(Optional.empty());
            given(cartRepository.save(any())).willReturn(savedCart);
            given(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).willReturn(Optional.empty());
            given(cartItemRepository.save(any())).willReturn(savedItem);

            Long result = sut.addItem(new AddCartItemCommand(FAN_ID, PRODUCT_ID, 3));

            assertEquals(CART_ITEM_ID, result);
            verify(cartRepository).save(any());
        }

        @Test
        @DisplayName("동일 상품 재담기 시 수량 합산")
        void addItem_duplicateProduct_mergesQuantity() {
            CartItem existing = cartItem(2);
            CartItem updated = CartItem.of(CART_ITEM_ID, CART_ID, PRODUCT_ID, 5, LocalDateTime.now());
            given(cartRepository.findByFanId(FAN_ID)).willReturn(Optional.of(cart()));
            given(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).willReturn(Optional.of(existing));
            given(cartItemRepository.save(any())).willReturn(updated);

            sut.addItem(new AddCartItemCommand(FAN_ID, PRODUCT_ID, 3));

            ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
            verify(cartItemRepository).save(captor.capture());
            assertEquals(5, captor.getValue().getQuantity());  // 2 + 3
            verify(cartRepository, never()).save(any());        // 장바구니 재생성 없음
        }

        @Test
        @DisplayName("장바구니 있고 신규 상품이면 신규 아이템 추가")
        void addItem_newProduct_createsItem() {
            CartItem savedItem = cartItem(1);
            given(cartRepository.findByFanId(FAN_ID)).willReturn(Optional.of(cart()));
            given(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).willReturn(Optional.empty());
            given(cartItemRepository.save(any())).willReturn(savedItem);

            Long result = sut.addItem(new AddCartItemCommand(FAN_ID, PRODUCT_ID, 1));

            assertEquals(CART_ITEM_ID, result);
            verify(cartRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateItemQuantity()")
    class UpdateItemQuantity {

        @Test
        @DisplayName("수량 변경 성공 — 소유권 검증 포함")
        void updateItemQuantity_success() {
            CartItem item = cartItem(2);
            given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.of(item));
            given(cartRepository.findByFanId(FAN_ID)).willReturn(Optional.of(cart()));
            given(cartItemRepository.save(any())).willReturn(item);

            sut.updateItemQuantity(new UpdateCartItemCommand(FAN_ID, CART_ITEM_ID, 5));

            ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
            verify(cartItemRepository).save(captor.capture());
            assertEquals(5, captor.getValue().getQuantity());
        }

        @Test
        @DisplayName("타인 항목 수정 시 CartAccessDeniedException")
        void updateItemQuantity_wrongOwner_throws() {
            CartItem item = cartItem(2);
            Cart otherCart = Cart.of(999L, 999L, LocalDateTime.now(), LocalDateTime.now());
            given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.of(item));
            given(cartRepository.findByFanId(FAN_ID)).willReturn(Optional.of(otherCart));

            assertThrows(CartAccessDeniedException.class,
                    () -> sut.updateItemQuantity(new UpdateCartItemCommand(FAN_ID, CART_ITEM_ID, 3)));

            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 항목이면 CartItemNotFoundException")
        void updateItemQuantity_notFound_throws() {
            given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.empty());

            assertThrows(CartItemNotFoundException.class,
                    () -> sut.updateItemQuantity(new UpdateCartItemCommand(FAN_ID, CART_ITEM_ID, 3)));

            verify(cartItemRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("removeItem()")
    class RemoveItem {

        @Test
        @DisplayName("항목 삭제 성공 — 소유권 검증 포함")
        void removeItem_success() {
            given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.of(cartItem(1)));
            given(cartRepository.findByFanId(FAN_ID)).willReturn(Optional.of(cart()));

            sut.removeItem(FAN_ID, CART_ITEM_ID);

            verify(cartItemRepository).deleteById(CART_ITEM_ID);
        }

        @Test
        @DisplayName("타인 항목 삭제 시 CartAccessDeniedException")
        void removeItem_wrongOwner_throws() {
            CartItem item = cartItem(1);
            Cart otherCart = Cart.of(999L, 999L, LocalDateTime.now(), LocalDateTime.now());
            given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.of(item));
            given(cartRepository.findByFanId(FAN_ID)).willReturn(Optional.of(otherCart));

            assertThrows(CartAccessDeniedException.class, () -> sut.removeItem(FAN_ID, CART_ITEM_ID));

            verify(cartItemRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("존재하지 않는 항목이면 CartItemNotFoundException")
        void removeItem_notFound_throws() {
            given(cartItemRepository.findById(CART_ITEM_ID)).willReturn(Optional.empty());

            assertThrows(CartItemNotFoundException.class, () -> sut.removeItem(FAN_ID, CART_ITEM_ID));

            verify(cartItemRepository, never()).deleteById(any());
        }
    }
}
