package com.fandrops.community.application;

import com.fandrops.community.application.port.ArtistProfilePort;
import com.fandrops.user.application.event.AgencyApprovedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

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
}