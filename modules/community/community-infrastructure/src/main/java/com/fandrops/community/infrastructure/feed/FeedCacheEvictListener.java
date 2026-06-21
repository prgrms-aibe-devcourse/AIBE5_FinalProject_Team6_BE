package com.fandrops.community.infrastructure.feed;

import com.fandrops.community.application.feed.FeedCacheEvictEvent;
import com.fandrops.community.application.port.FeedCachePort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// FeedCacheAdapter는 FeedCachePort 인터페이스를 구현하므로 JDK 프록시로 래핑됨.
// @TransactionalEventListener + @Async를 같은 빈에 두면 onFeedCacheEvict가 인터페이스에 없어
// 프록시 탐색 실패(BeanInitializationException) 발생 → 인터페이스 미구현 단독 클래스로 분리.
@Component
public class FeedCacheEvictListener {

    private final FeedCachePort feedCachePort;

    public FeedCacheEvictListener(FeedCachePort feedCachePort) {
        this.feedCachePort = feedCachePort;
    }

    @Async("communityEvictExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedCacheEvict(FeedCacheEvictEvent event) {
        feedCachePort.evictByArtistId(event.artistId());
    }
}
