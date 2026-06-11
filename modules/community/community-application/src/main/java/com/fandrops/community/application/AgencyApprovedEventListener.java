package com.fandrops.community.application;

import com.fandrops.community.application.port.ArtistProfilePort;
import com.fandrops.user.application.event.AgencyApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AgencyApprovedEventListener {

    private static final Logger log = LoggerFactory.getLogger(AgencyApprovedEventListener.class);

    private final ArtistProfilePort artistProfilePort;

    public AgencyApprovedEventListener(ArtistProfilePort artistProfilePort) {
        this.artistProfilePort = artistProfilePort;
    }

    // fallbackExecution=false(기본값): 트랜잭션 없는 컨텍스트에서 publishEvent()하면 이 리스너는 실행되지 않음 — 의도된 동작
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAgencyApproved(AgencyApprovedEvent event) {
        log.info("입점 승인 이벤트 수신 — artistId={}, agencyId={}, artistName={}",
                event.getArtistId(), event.getAgencyId(), event.getArtistName());
        try {
            artistProfilePort.activate(event.getArtistId());
        } catch (Exception e) {
            log.error("아티스트 공간 활성화 실패 — artistId={}", event.getArtistId(), e);
        }
    }
}