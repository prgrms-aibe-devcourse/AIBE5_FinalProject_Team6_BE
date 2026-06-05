package com.fandrops.order.application;

import com.fandrops.order.application.dto.AddCartItemCommand;
import com.fandrops.order.application.dto.CartItemResponse;
import com.fandrops.order.application.dto.CartResponse;
import com.fandrops.order.application.dto.UpdateCartItemCommand;
import com.fandrops.order.domain.Cart;
import com.fandrops.order.domain.CartItem;
import com.fandrops.order.domain.exception.CartItemNotFoundException;
import com.fandrops.order.domain.exception.CartNotFoundException;
import com.fandrops.order.domain.port.CartItemRepository;
import com.fandrops.order.domain.port.CartRepository;
import com.fandrops.order.domain.port.ProductPricePort;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductPricePort productPricePort;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductPricePort productPricePort) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productPricePort = productPricePort;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long fanId) {
        Optional<Cart> cart = cartRepository.findByFanId(fanId);
        if (cart.isEmpty()) {
            return new CartResponse(List.of());
        }
        List<CartItem> items = cartItemRepository.findAllByCartId(cart.get().getId());
        List<CartItemResponse> responses = items.stream()
                .map(item -> new CartItemResponse(
                        item.getId(),
                        item.getProductId(),
                        item.getQuantity(),
                        productPricePort.getPrice(item.getProductId())))
                .toList();
        return new CartResponse(responses);
    }

    @Transactional
    public Long addItem(AddCartItemCommand command) {
        Cart cart = cartRepository.findByFanId(command.getFanId())
                .orElseGet(() -> cartRepository.save(Cart.create(command.getFanId())));

        Optional<CartItem> existing = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), command.getProductId());
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.updateQuantity(item.getQuantity() + command.getQuantity());
            return cartItemRepository.save(item).getId();
        }

        CartItem newItem = CartItem.create(cart.getId(), command.getProductId(), command.getQuantity());
        return cartItemRepository.save(newItem).getId();
    }

    @Transactional
    public void updateItemQuantity(UpdateCartItemCommand command) {
        CartItem item = cartItemRepository.findById(command.getCartItemId())
                .orElseThrow(() -> new CartItemNotFoundException(command.getCartItemId()));
        verifyOwnership(command.getFanId(), item.getCartId());
        item.updateQuantity(command.getQuantity());
        cartItemRepository.save(item);
    }

    @Transactional
    public void removeItem(Long fanId, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));
        verifyOwnership(fanId, item.getCartId());
        cartItemRepository.deleteById(cartItemId);
    }

    // cartId가 요청 fanId의 장바구니인지 검증
    private void verifyOwnership(Long fanId, Long cartId) {
        Cart cart = cartRepository.findByFanId(fanId)
                .orElseThrow(() -> new CartNotFoundException(fanId));
        if (!cart.getId().equals(cartId)) {
            throw new IllegalArgumentException("본인의 장바구니 항목만 수정할 수 있습니다.");
        }
    }
}
