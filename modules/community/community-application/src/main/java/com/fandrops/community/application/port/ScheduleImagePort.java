package com.fandrops.community.application.port;

import java.util.List;
import java.util.Map;

public interface ScheduleImagePort {
    void saveAll(Long scheduleId, List<String> imageUrls);
    Map<Long, List<String>> findByScheduleIds(List<Long> scheduleIds);
    List<String> findByScheduleId(Long scheduleId);
}
