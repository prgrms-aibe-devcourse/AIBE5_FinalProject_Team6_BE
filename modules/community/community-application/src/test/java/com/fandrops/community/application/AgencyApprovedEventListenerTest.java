package com.fandrops.community.application;

import com.fandrops.community.application.port.ArtistProfilePort;
import com.fandrops.user.application.event.AgencyApprovedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgencyApprovedEventListenerTest {

    @Mock
    ArtistProfilePort artistProfilePort;

    AgencyApprovedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AgencyApprovedEventListener(artistProfilePort);
    }

    @Test
    @DisplayName("입점 승인 이벤트 수신 — artistId로 activate() 호출")
    void handleAgencyApproved_callsActivate() {
        AgencyApprovedEvent event = new AgencyApprovedEvent(10L, 1L, "테스트아티스트");

        listener.handleAgencyApproved(event);

        verify(artistProfilePort).activate(10L);
    }

    @Test
    @DisplayName("activate() 항상 실패 시 MAX_ACTIVATE_ATTEMPTS 횟수 재시도 후 예외 미전파")
    void handleAgencyApproved_activateAlwaysThrows_retriesAndDoesNotPropagate() {
        doThrow(new RuntimeException("DB 오류")).when(artistProfilePort).activate(10L);
        AgencyApprovedEvent event = new AgencyApprovedEvent(10L, 1L, "테스트아티스트");

        assertDoesNotThrow(() -> listener.handleAgencyApproved(event));

        verify(artistProfilePort, times(AgencyApprovedEventListener.MAX_ACTIVATE_ATTEMPTS)).activate(10L);
    }

    @Test
    @DisplayName("activate() 첫 번째 시도 실패, 두 번째 성공 — 총 2회 호출")
    void handleAgencyApproved_activateSucceedsOnSecondAttempt() {
        doThrow(new RuntimeException("일시적 DB 오류"))
                .doNothing()
                .when(artistProfilePort).activate(10L);
        AgencyApprovedEvent event = new AgencyApprovedEvent(10L, 1L, "테스트아티스트");

        assertDoesNotThrow(() -> listener.handleAgencyApproved(event));

        verify(artistProfilePort, times(2)).activate(10L);
    }
}