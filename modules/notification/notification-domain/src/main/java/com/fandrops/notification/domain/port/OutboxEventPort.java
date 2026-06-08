package com.fandrops.notification.domain.port;

import com.fandrops.notification.domain.OutboxEvent;
import java.util.List;

public interface OutboxEventPort {
    OutboxEvent save(OutboxEvent outboxEvent);
    List<OutboxEvent> findPending(int limit);
    void update(OutboxEvent outboxEvent);
}
