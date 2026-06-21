package com.fandrops.community.infrastructure.feed;

import com.fandrops.community.application.feed.FeedCacheEvictEvent;
import com.fandrops.community.application.port.FeedCachePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedCacheEvictListenerTest {

    @Mock FeedCachePort feedCachePort;

    FeedCacheEvictListener listener;

    @BeforeEach
    void setUp() {
        listener = new FeedCacheEvictListener(feedCachePort);
    }

    @Test
    @DisplayName("이벤트 수신 시 artistId로 feedCachePort.evictByArtistId 호출")
    void onFeedCacheEvict_delegatesToPort() {
        FeedCacheEvictEvent event = new FeedCacheEvictEvent(42L);

        listener.onFeedCacheEvict(event);

        verify(feedCachePort).evictByArtistId(42L);
    }
}