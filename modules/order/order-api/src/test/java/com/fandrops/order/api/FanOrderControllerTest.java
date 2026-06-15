package com.fandrops.order.api;

import com.fandrops.order.application.OrderService;
import com.fandrops.order.application.dto.OrderListItemResponse;
import com.fandrops.order.application.dto.OrderListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FanOrderController 단위 테스트")
class FanOrderControllerTest {

    @Mock private OrderService orderService;
    @Mock private Environment environment;

    @InjectMocks
    private FanOrderController sut;

    private static final Long FAN_ID = 100L;

    private Authentication authWith(Long fanId) {
        Authentication auth = mock(Authentication.class);
        given(auth.isAuthenticated()).willReturn(true);
        given(auth.getPrincipal()).willReturn(fanId.toString());
        given(auth.getName()).willReturn(fanId.toString());
        return auth;
    }

    @Nested
    @DisplayName("GET /fans/me/orders")
    class GetMyOrders {

        @Test
        @DisplayName("JWT 인증 → orderService.getMyOrders 호출 후 200 반환")
        void authenticated_returns200() {
            OrderListResponse stub = new OrderListResponse(List.of(), null);
            given(orderService.getMyOrders(FAN_ID, null, 20)).willReturn(stub);

            ResponseEntity<?> response = sut.getMyOrders(authWith(FAN_ID), null, null, 20);

            assertEquals(200, response.getStatusCode().value());
            verify(orderService).getMyOrders(FAN_ID, null, 20);
        }

        @Test
        @DisplayName("size > 100이면 100으로 제한")
        void sizeExceedsMax_cappedAt100() {
            OrderListResponse stub = new OrderListResponse(List.of(), null);
            given(orderService.getMyOrders(FAN_ID, null, 100)).willReturn(stub);

            sut.getMyOrders(authWith(FAN_ID), null, null, 999);

            verify(orderService).getMyOrders(FAN_ID, null, 100);
        }

        @Test
        @DisplayName("cursor 전달 시 orderService에 그대로 전달")
        void cursor_passedToService() {
            Long cursor = 50L;
            OrderListResponse stub = new OrderListResponse(List.of(), null);
            given(orderService.getMyOrders(FAN_ID, cursor, 20)).willReturn(stub);

            sut.getMyOrders(authWith(FAN_ID), null, cursor, 20);

            verify(orderService).getMyOrders(FAN_ID, cursor, 20);
        }

        @Test
        @DisplayName("인증 없으면 IllegalArgumentException")
        void noAuth_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> sut.getMyOrders(null, null, null, 20));
        }
    }
}
